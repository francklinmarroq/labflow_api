package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Listado de etiquetas de orden. Conserva la forma paginada del resto de los
 * catálogos (el frontend las lee con el mismo helper), aunque siempre viene en
 * una sola página: un laboratorio maneja decenas de etiquetas, no miles.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTagResponse {
    private List<OrderTagDTO> content;
    private Integer pageNumber;
    private Integer pageSize;
    private Long totalElements;
    private Integer totalPages;
    private boolean lastPage;
}
