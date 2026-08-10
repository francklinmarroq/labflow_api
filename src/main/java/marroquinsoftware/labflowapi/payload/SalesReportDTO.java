package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reporte de ventas de un periodo, armado en una sola respuesta: el resumen de
 * lo facturado (excluyendo facturas anuladas), el desglose por periodo (día o
 * mes), por examen y por cliente. Los montos son los congelados en la factura.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesReportDTO {

    private LocalDate from;
    private LocalDate to;
    /** "day" o "month": granularidad del desglose por periodo. */
    private String groupBy;
    private Summary summary;
    private List<PeriodBucket> byPeriod;
    private List<TestSales> byTest;
    private List<CustomerSales> byCustomer;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private long count;
        private BigDecimal subtotal;
        private BigDecimal discountAmount;
        private BigDecimal otherDiscountAmount;
        private BigDecimal total;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodBucket {
        /** "YYYY-MM-DD" cuando es por día, "YYYY-MM" cuando es por mes. */
        private String bucket;
        private BigDecimal total;
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestSales {
        private String testName;
        private long qty;
        private BigDecimal amount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerSales {
        private String customerName;
        private long count;
        private BigDecimal amount;
    }
}
