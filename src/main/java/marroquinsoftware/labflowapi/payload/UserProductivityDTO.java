package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reporte de producción por usuario en un periodo: facturas emitidas y monto
 * vendido (por usuario emisor) y pagos recibidos y su monto (por usuario que
 * cobró). El sistema no registra quién crea cada orden, así que la producción se
 * mide sobre facturación y cobros, que sí guardan el usuario responsable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProductivityDTO {

    private LocalDate from;
    private LocalDate to;
    private List<Row> rows;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private String username;
        private long invoicesIssued;
        private BigDecimal salesAmount;
        private long paymentsCount;
        private BigDecimal paymentsAmount;
    }
}
