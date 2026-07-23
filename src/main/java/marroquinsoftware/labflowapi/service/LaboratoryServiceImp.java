package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.payload.LaboratoryDTO;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Service
public class LaboratoryServiceImp implements LaboratoryService {

    /** Cuánto dura la URL firmada del logo. Alcanza de sobra para abrir e imprimir un reporte. */
    private static final Duration LOGO_URL_TTL = Duration.ofHours(6);

    /** 2 MB: un logo de membrete no necesita más y evita subidas accidentales de fotos enormes. */
    private static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;

    /** Formatos que los navegadores imprimen sin problema. */
    private static final Map<String, String> ALLOWED_LOGO_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp");

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private FileStorageService fileStorageService;

    @Override
    public LaboratoryDTO getLaboratory() {
        return toDto(findOwn());
    }

    @Override
    public LaboratoryDTO createLaboratory(LaboratoryDTO dto) {
        // El laboratorio se crea al registrar la cuenta (RegistrationService). No se
        // permite crear otro desde aquí para no dejar laboratorios sin dueño.
        throw new APIException("El laboratorio se crea al registrar la cuenta. Use PUT para actualizarlo.");
    }

    @Override
    public LaboratoryDTO updateLaboratory(LaboratoryDTO dto, Long id) {
        Long tenant = requireTenant();
        if (!tenant.equals(id)) {
            throw new APIException("No puede modificar un laboratorio distinto al suyo.");
        }
        Laboratory laboratory = laboratoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", "id", id));
        // El owner no viene en el DTO, así que modelMapper no lo toca y se conserva.
        String logoKey = laboratory.getLogoObjectKey();
        String stampKey = laboratory.getStampObjectKey();
        modelMapper.map(dto, laboratory);
        laboratory.setId(id);
        // Ni el logo ni el sello viajan en el DTO (solo sus URL firmadas, que son de
        // lectura): se reponen las llaves que ya estaban guardadas.
        laboratory.setLogoObjectKey(logoKey);
        laboratory.setStampObjectKey(stampKey);
        return toDto(laboratoryRepository.save(laboratory));
    }

    @Override
    public LaboratoryDTO uploadLogo(MultipartFile file) {
        return replaceImage(file, "logo", "El logo",
                Laboratory::getLogoObjectKey, Laboratory::setLogoObjectKey);
    }

    @Override
    public LaboratoryDTO deleteLogo() {
        return clearImage(Laboratory::getLogoObjectKey, Laboratory::setLogoObjectKey);
    }

    @Override
    public LaboratoryDTO uploadStamp(MultipartFile file) {
        return replaceImage(file, "sello", "El sello",
                Laboratory::getStampObjectKey, Laboratory::setStampObjectKey);
    }

    @Override
    public LaboratoryDTO deleteStamp() {
        return clearImage(Laboratory::getStampObjectKey, Laboratory::setStampObjectKey);
    }

    /**
     * Sube una imagen del membrete (logo o sello) y la deja apuntada en el
     * laboratorio, borrando la anterior. Logo y sello comparten reglas: mismo
     * límite de tamaño, mismos formatos y misma carpeta del bucket.
     *
     * @param namePrefix prefijo del archivo en el bucket ({@code logo}, {@code sello})
     * @param label      cómo se nombra la imagen en los mensajes de error
     */
    private LaboratoryDTO replaceImage(MultipartFile file, String namePrefix, String label,
                                       Function<Laboratory, String> getKey,
                                       BiConsumer<Laboratory, String> setKey) {
        if (file == null || file.isEmpty()) {
            throw new APIException("Seleccione una imagen para %s.".formatted(label.toLowerCase(Locale.ROOT)));
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new APIException("La imagen no puede pesar más de 2 MB.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = ALLOWED_LOGO_TYPES.get(contentType);
        if (extension == null) {
            throw new APIException("%s debe ser una imagen PNG, JPG o WEBP.".formatted(label));
        }

        Laboratory laboratory = findOwn();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new APIException("No se pudo leer la imagen. Intente de nuevo.");
        }

        String previousKey = getKey.apply(laboratory);
        // Nombre nuevo en cada subida: si se reusara el mismo, la caché del
        // navegador seguiría mostrando la imagen vieja en los reportes.
        String key = "%s/%s-%s.%s".formatted(
                tenantFolder(laboratory), namePrefix, UUID.randomUUID().toString().substring(0, 8), extension);
        fileStorageService.upload(key, bytes, contentType);

        setKey.accept(laboratory, key);
        Laboratory saved = laboratoryRepository.save(laboratory);

        // Recién cuando el registro apunta al archivo nuevo se borra el anterior.
        if (previousKey != null && !previousKey.equals(key)) {
            fileStorageService.delete(previousKey);
        }
        return toDto(saved);
    }

    private LaboratoryDTO clearImage(Function<Laboratory, String> getKey,
                                     BiConsumer<Laboratory, String> setKey) {
        Laboratory laboratory = findOwn();
        String key = getKey.apply(laboratory);
        setKey.accept(laboratory, null);
        Laboratory saved = laboratoryRepository.save(laboratory);
        fileStorageService.delete(key);
        return toDto(saved);
    }

    /**
     * Carpeta del laboratorio dentro del bucket: {@code laboratorios/12-lab-san-jose}.
     * Lleva el id por delante (es único y no cambia aunque el laboratorio se
     * renombre) y el nombre detrás, para reconocer la carpeta a simple vista
     * desde el panel de Cloudflare.
     */
    private String tenantFolder(Laboratory laboratory) {
        return "laboratorios/%d-%s".formatted(laboratory.getId(), slugify(laboratory.getName()));
    }

    private static String slugify(String value) {
        if (value == null || value.isBlank()) return "lab";
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (slug.isBlank()) return "lab";
        return slug.length() > 60 ? slug.substring(0, 60) : slug;
    }

    private Laboratory findOwn() {
        Long tenant = requireTenant();
        return laboratoryRepository.findById(tenant)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", "id", tenant));
    }

    private LaboratoryDTO toDto(Laboratory laboratory) {
        LaboratoryDTO dto = modelMapper.map(laboratory, LaboratoryDTO.class);
        dto.setLogoUrl(fileStorageService.signedUrl(laboratory.getLogoObjectKey(), LOGO_URL_TTL));
        dto.setStampUrl(fileStorageService.signedUrl(laboratory.getStampObjectKey(), LOGO_URL_TTL));
        return dto;
    }

    private Long requireTenant() {
        Long laboratoryId = TenantContext.getLaboratoryId();
        if (laboratoryId == null) {
            throw new APIException("No hay un laboratorio asociado a la sesión actual");
        }
        return laboratoryId;
    }
}
