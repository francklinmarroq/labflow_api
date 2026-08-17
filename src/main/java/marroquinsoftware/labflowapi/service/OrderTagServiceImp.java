package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.OrderTag;
import marroquinsoftware.labflowapi.payload.OrderTagDTO;
import marroquinsoftware.labflowapi.payload.OrderTagResponse;
import marroquinsoftware.labflowapi.repositories.OrderTagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class OrderTagServiceImp implements OrderTagService {

    private static final int MAX_NAME_LENGTH = 60;

    @Autowired
    private OrderTagRepository orderTagRepository;

    @Override
    @Transactional(readOnly = true)
    public OrderTagResponse getAllTags() {
        List<OrderTag> tags = orderTagRepository.findAllByOrderByNameAsc();
        Map<Long, Long> usage = usageByTagId();
        List<OrderTagDTO> content = tags.stream()
                .map(tag -> toDTO(tag, usage.getOrDefault(tag.getId(), 0L)))
                .toList();
        // Se devuelven todas en una sola página: son decenas, no miles.
        return new OrderTagResponse(content, 0, content.size(), (long) content.size(), 1, true);
    }

    @Override
    @Transactional
    public OrderTagDTO createTag(OrderTagDTO dto) {
        String name = cleanName(dto.getName());
        String normalized = normalize(name);
        orderTagRepository.findByNormalizedName(normalized).ifPresent(existing -> {
            throw new APIException("Ya existe la etiqueta '" + existing.getName() + "'.");
        });
        OrderTag tag = new OrderTag();
        // El laboratorio (tenant) lo asigna Hibernate al persistir por @TenantId.
        tag.setName(name);
        tag.setNormalizedName(normalized);
        tag.setColor(cleanColor(dto.getColor()));
        return toDTO(orderTagRepository.save(tag), 0L);
    }

    @Override
    @Transactional
    public OrderTagDTO updateTag(OrderTagDTO dto, Long id) {
        OrderTag tag = orderTagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderTag", "tagId", id));
        String name = cleanName(dto.getName());
        String normalized = normalize(name);
        // Renombrar a algo que ya existe fusionaría dos etiquetas distintas; se
        // rechaza. La comparación es la normalizada, así que solo cambiar tildes o
        // mayúsculas ("ihss" -> "IHSS") sí se permite: es la misma etiqueta.
        Optional<OrderTag> clash = orderTagRepository.findByNormalizedName(normalized);
        if (clash.isPresent() && !clash.get().getId().equals(id)) {
            throw new APIException("Ya existe la etiqueta '" + clash.get().getName() + "'.");
        }
        tag.setName(name);
        tag.setNormalizedName(normalized);
        tag.setColor(cleanColor(dto.getColor()));
        return toDTO(orderTagRepository.save(tag), usageByTagId().getOrDefault(id, 0L));
    }

    @Override
    @Transactional
    public OrderTagDTO deleteTag(Long id) {
        OrderTag tag = orderTagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderTag", "tagId", id));
        OrderTagDTO dto = toDTO(tag, null);
        // La dueña de la relación es LabOrder, así que borrar la etiqueta NO limpia
        // sola las filas de lab_order_tags: sin esto, borrar una etiqueta en uso
        // reventaría con violación de llave foránea. Se desengancha de las órdenes
        // primero; las órdenes quedan intactas, solo pierden esa clasificación.
        orderTagRepository.detachFromAllOrders(id);
        orderTagRepository.delete(tag);
        return dto;
    }

    @Override
    @Transactional
    public Set<OrderTag> resolveOrCreate(List<String> names) {
        Set<OrderTag> result = new LinkedHashSet<>();
        if (names == null || names.isEmpty()) {
            return result;
        }

        // Se normaliza primero todo lo que llegó, descartando vacíos y repetidos,
        // para resolver el lote con UNA consulta en vez de una por nombre.
        Map<String, String> namesByNormalized = new LinkedHashMap<>();
        for (String raw : names) {
            if (raw == null || raw.isBlank()) continue;
            String name = cleanName(raw);
            namesByNormalized.putIfAbsent(normalize(name), name);
        }
        if (namesByNormalized.isEmpty()) {
            return result;
        }

        Map<String, OrderTag> existing = new HashMap<>();
        for (OrderTag tag : orderTagRepository.findByNormalizedNameIn(namesByNormalized.keySet())) {
            existing.put(tag.getNormalizedName(), tag);
        }

        List<OrderTag> toCreate = new ArrayList<>();
        for (Map.Entry<String, String> entry : namesByNormalized.entrySet()) {
            OrderTag tag = existing.get(entry.getKey());
            if (tag == null) {
                // Primera vez que se usa este nombre en el laboratorio: se da de alta
                // sola, sin pasar por el catálogo. Ese es el punto de la feature.
                tag = new OrderTag();
                tag.setName(entry.getValue());
                tag.setNormalizedName(entry.getKey());
                toCreate.add(tag);
            }
            result.add(tag);
        }
        if (!toCreate.isEmpty()) {
            orderTagRepository.saveAll(toCreate);
        }
        return result;
    }

    // --- Helpers ---

    private Map<Long, Long> usageByTagId() {
        Map<Long, Long> usage = new HashMap<>();
        for (Object[] row : orderTagRepository.countUsageByTag()) {
            usage.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return usage;
    }

    /** Recorta, colapsa espacios repetidos y corta al largo de la columna. */
    private String cleanName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new APIException("Escriba el nombre de la etiqueta.");
        }
        String name = raw.trim().replaceAll("\\s+", " ");
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    /**
     * Llave de comparación de la etiqueta: sin tildes, sin espacios de más y en
     * minúsculas, para que "IHSS", "ihss" y "Ihss" sean la misma y no se llene el
     * catálogo de variantes del mismo convenio. Se descompone en NFD y se quitan
     * los diacríticos, así "Régimen" y "Regimen" también coinciden.
     */
    private String normalize(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase();
    }

    /** Acepta solo un hexadecimal #rrggbb; cualquier otra cosa se guarda como sin color. */
    private String cleanColor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String color = raw.trim();
        return color.matches("^#[0-9a-fA-F]{6}$") ? color.toLowerCase() : null;
    }

    private OrderTagDTO toDTO(OrderTag tag, Long orderCount) {
        return new OrderTagDTO(tag.getId(), tag.getName(), tag.getColor(), orderCount);
    }
}
