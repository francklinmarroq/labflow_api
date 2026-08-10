package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reporte de cobros de un periodo: los pagos activos recibidos en el rango,
 * con su resumen, el desglose por método de pago y por día, más el saldo total
 * por cobrar actual (independiente del rango, es una foto del momento).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectionsReportDTO {

    private LocalDate from;
    private LocalDate to;
    private Summary summary;
    private List<MethodBucket> byMethod;
    private List<DayBucket> byDay;
    /** Saldo pendiente de cobro al momento de consultar (no depende del rango). */
    private BigDecimal currentReceivable;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private long count;
        private BigDecimal total;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodBucket {
        private PaymentMethod method;
        private String methodLabel;
        private BigDecimal total;
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayBucket {
        /** "YYYY-MM-DD" en zona horaria de Honduras. */
        private String day;
        private BigDecimal total;
    }
}
