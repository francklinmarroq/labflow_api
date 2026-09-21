package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Listado de médicos solicitantes. Conserva la forma paginada del resto de los
 * catálogos (el frontend los lee con el mismo helper), aunque siempre viene en
 * una sola página: un laboratorio maneja decenas de médicos, no miles.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferringPhysicianResponse {
    private List<ReferringPhysicianDTO> content;
    private Integer pageNumber;
    private Integer pageSize;
    private Long totalElements;
    private Integer totalPages;
    private boolean lastPage;
}
