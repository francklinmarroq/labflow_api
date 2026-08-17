package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.OrderTag;
import marroquinsoftware.labflowapi.payload.OrderTagDTO;
import marroquinsoftware.labflowapi.payload.OrderTagResponse;

import java.util.List;
import java.util.Set;

public interface OrderTagService {

    /** Etiquetas del laboratorio ordenadas por nombre, con el conteo de órdenes que las usan. */
    OrderTagResponse getAllTags();

    OrderTagDTO createTag(OrderTagDTO dto);

    /** Renombra o cambia el color. Renombrar afecta a todas las órdenes ya etiquetadas. */
    OrderTagDTO updateTag(OrderTagDTO dto, Long id);

    /** Borra la etiqueta y la quita de las órdenes que la tenían; no toca las órdenes. */
    OrderTagDTO deleteTag(Long id);

    /**
     * Traduce los nombres escritos en una orden a etiquetas, creando las que aún
     * no existan en el laboratorio. Es el "se guardan la primera vez que se usen":
     * quien levanta la orden escribe "IHSS" y la etiqueta queda disponible para las
     * siguientes órdenes sin haber pasado por el catálogo.
     *
     * <p>Devuelve el conjunto en el mismo orden en que venían los nombres,
     * ignorando vacíos y repetidos (comparando sin tildes ni mayúsculas).
     */
    Set<OrderTag> resolveOrCreate(List<String> names);
}
