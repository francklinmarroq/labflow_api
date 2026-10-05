package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.InvoiceItemType;
import marroquinsoftware.labflowapi.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Registro de ventas detallado de un periodo: una fila por línea de factura,
 * con el descuento de la factura repartido entre sus líneas y el desglose
 * fiscal tal como lo declara la factura. Las facturas anuladas salen con sus
 * filas en cero para que el correlativo quede completo; los totales cuentan
 * solo las vigentes y cuadran con el reporte de ventas resumido.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesRegisterDTO {

    private LocalDate from;
    private LocalDate to;
    private List<Row> rows;
    private Totals totals;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private Long invoiceId;
        private Instant issuedAt;
        /** Día de emisión en la zona del laboratorio, el que se usa para filtrar. */
        private LocalDate issueDate;
        private String invoiceNumber;
        private InvoiceStatus status;
        /** Números de las órdenes que cubre la factura; vacío si se facturó sin órdenes. */
        private List<Long> orderNumbers;
        private String customerName;
        private String description;
        private InvoiceItemType itemType;
        private BigDecimal quantity;
        /** Precio unitario de lista. */
        private BigDecimal unitPrice;
        /** Cantidad × precio unitario de lista. */
        private BigDecimal subtotal;
        /** Precio especial + parte del descuento por edad + parte de la otra rebaja. */
        private BigDecimal discount;
        private BigDecimal exempt;
        private BigDecimal taxed15;
        private BigDecimal taxed18;
        private BigDecimal tax15;
        private BigDecimal tax18;
        private BigDecimal total;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Totals {
        private BigDecimal subtotal;
        private BigDecimal discount;
        private BigDecimal exempt;
        private BigDecimal taxed15;
        private BigDecimal taxed18;
        private BigDecimal tax15;
        private BigDecimal tax18;
        private BigDecimal total;
    }
}
