package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.TestArea;

import java.time.LocalDate;
import java.util.List;

/**
 * Reporte de volumen de exámenes: cuántas veces se realizó cada examen en un
 * rango de fechas (por orden solicitada y no cancelada). El {@code area} sale
 * como nombre del enum; el frontend lo traduce a su etiqueta.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestsVolumeDTO {

    private LocalDate from;
    private LocalDate to;
    private List<Row> rows;
    /** Suma de todos los conteos (total de exámenes realizados en el rango). */
    private long total;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private Long testId;
        private String testName;
        private TestArea area;
        private long count;
    }
}
