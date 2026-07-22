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
        modelMapper.map(dto, laboratory);
        laboratory.setId(id);
        // El logo tampoco viaja en el DTO (solo la URL firmada, que es de lectura):
        // se repone la llave que ya estaba guardada.
        laboratory.setLogoObjectKey(logoKey);
        return toDto(laboratoryRepository.save(laboratory));
    }

    @Override
    public LaboratoryDTO uploadLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new APIException("Seleccione una imagen para el logo.");
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new APIException("La imagen no puede pesar más de 2 MB.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = ALLOWED_LOGO_TYPES.get(contentType);
        if (extension == null) {
            throw new APIException("El logo debe ser una imagen PNG, JPG o WEBP.");
        }

        Laboratory laboratory = findOwn();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new APIException("No se pudo leer la imagen. Intente de nuevo.");
        }

        String previousKey = laboratory.getLogoObjectKey();
        // Nombre nuevo en cada subida: si se reusara el mismo, la caché del
        // navegador seguiría mostrando el logo viejo en los reportes.
        String key = "%s/logo-%s.%s".formatted(
                tenantFolder(laboratory), UUID.randomUUID().toString().substring(0, 8), extension);
        fileStorageService.upload(key, bytes, contentType);

        laboratory.setLogoObjectKey(key);
        Laboratory saved = laboratoryRepository.save(laboratory);

        // Recién cuando el registro apunta al archivo nuevo se borra el anterior.
        if (previousKey != null && !previousKey.equals(key)) {
            fileStorageService.delete(previousKey);
        }
        return toDto(saved);
    }

    @Override
    public LaboratoryDTO deleteLogo() {
        Laboratory laboratory = findOwn();
        String key = laboratory.getLogoObjectKey();
        laboratory.setLogoObjectKey(null);
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
