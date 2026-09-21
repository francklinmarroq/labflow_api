package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.ReferringPhysician;
import marroquinsoftware.labflowapi.payload.ReferringPhysicianDTO;
import marroquinsoftware.labflowapi.payload.ReferringPhysicianResponse;
import marroquinsoftware.labflowapi.repositories.ReferringPhysicianRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReferringPhysicianServiceImp implements ReferringPhysicianService {

    private static final int MAX_NAME_LENGTH = 150;

    @Autowired
    private ReferringPhysicianRepository referringPhysicianRepository;

    @Override
    @Transactional(readOnly = true)
    public ReferringPhysicianResponse getAllPhysicians() {
        List<ReferringPhysician> physicians = referringPhysicianRepository.findAllByOrderByNameAsc();
        Map<Long, Long> usage = usageByPhysicianId();
        List<ReferringPhysicianDTO> content = physicians.stream()
                .map(physician -> toDTO(physician, usage.getOrDefault(physician.getId(), 0L)))
                .toList();
        // Se devuelven todos en una sola página: son decenas, no miles.
        return new ReferringPhysicianResponse(content, 0, content.size(), (long) content.size(), 1, true);
    }

    @Override
    @Transactional
    public ReferringPhysicianDTO createPhysician(ReferringPhysicianDTO dto) {
        String name = cleanName(dto.getName());
        String normalized = normalize(name);
        referringPhysicianRepository.findByNormalizedName(normalized).ifPresent(existing -> {
            throw new APIException("Ya existe el médico '" + existing.getName() + "'.");
        });
        ReferringPhysician physician = new ReferringPhysician();
        // El laboratorio (tenant) lo asigna Hibernate al persistir por @TenantId.
        physician.setName(name);
        physician.setNormalizedName(normalized);
        return toDTO(referringPhysicianRepository.save(physician), 0L);
    }

    @Override
    @Transactional
    public ReferringPhysicianDTO updatePhysician(ReferringPhysicianDTO dto, Long id) {
        ReferringPhysician physician = referringPhysicianRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReferringPhysician", "physicianId", id));
        String name = cleanName(dto.getName());
        String normalized = normalize(name);
        // Renombrar a alguien que ya existe fusionaría dos médicos distintos; se
        // rechaza. La comparación es la normalizada, así que solo cambiar tildes o
        // mayúsculas ("dra ana funez" -> "Dra. Ana Fúnez") sí se permite: es el mismo
        // médico corrigiendo cómo se escribe.
        Optional<ReferringPhysician> clash = referringPhysicianRepository.findByNormalizedName(normalized);
        if (clash.isPresent() && !clash.get().getId().equals(id)) {
            throw new APIException("Ya existe el médico '" + clash.get().getName() + "'.");
        }
        physician.setName(name);
        physician.setNormalizedName(normalized);
        return toDTO(referringPhysicianRepository.save(physician), usageByPhysicianId().getOrDefault(id, 0L));
    }

    @Override
    @Transactional
    public ReferringPhysicianDTO deletePhysician(Long id) {
        ReferringPhysician physician = referringPhysicianRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReferringPhysician", "physicianId", id));
        ReferringPhysicianDTO dto = toDTO(physician, null);
        // La orden apunta al médico con una llave foránea: sin esto, borrar uno que
        // esté en uso reventaría con violación de llave foránea. Se desengancha de
        // las órdenes primero; las órdenes quedan intactas, solo dejan de indicar
        // médico solicitante.
        referringPhysicianRepository.detachFromAllOrders(id);
        referringPhysicianRepository.delete(physician);
        return dto;
    }

    @Override
    @Transactional
    public ReferringPhysician resolveOrCreate(String name) {
        if (name == null || name.isBlank()) {
            // Sin nombre la orden simplemente no indica médico; no se crea nada.
            return null;
        }
        String cleaned = cleanName(name);
        String normalized = normalize(cleaned);
        Optional<ReferringPhysician> existing = referringPhysicianRepository.findByNormalizedName(normalized);
        if (existing.isPresent()) {
            return existing.get();
        }
        // Primera vez que se usa este nombre en el laboratorio: se da de alta solo,
        // sin pasar por el catálogo. Ese es el punto de la feature.
        ReferringPhysician physician = new ReferringPhysician();
        physician.setName(cleaned);
        physician.setNormalizedName(normalized);
        return referringPhysicianRepository.save(physician);
    }

    // --- Helpers ---

    private Map<Long, Long> usageByPhysicianId() {
        Map<Long, Long> usage = new HashMap<>();
        for (Object[] row : referringPhysicianRepository.countUsageByPhysician()) {
            usage.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return usage;
    }

    /** Recorta, colapsa espacios repetidos y corta al largo de la columna. */
    private String cleanName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new APIException("Escriba el nombre del médico.");
        }
        String name = raw.trim().replaceAll("\\s+", " ");
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    /**
     * Llave de comparación del médico: sin tildes, sin espacios de más y en
     * minúsculas, para que "Dra. Ana Fúnez", "dra. ana funez" y "Dra.  Ana Funez"
     * sean el mismo y no se llene el catálogo de variantes de la misma persona. Se
     * descompone en NFD y se quitan los diacríticos, así "Fúnez" y "Funez" también
     * coinciden.
     */
    private String normalize(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase();
    }

    private ReferringPhysicianDTO toDTO(ReferringPhysician physician, Long orderCount) {
        return new ReferringPhysicianDTO(physician.getId(), physician.getName(), orderCount);
    }
}
