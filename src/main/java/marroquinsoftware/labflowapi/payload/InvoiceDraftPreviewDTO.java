package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.AgeDiscountKind;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cómo saldría la factura del borrador: líneas ya agrupadas, órdenes con su
 * descuento, destinatario resuelto y totales. Son los mismos números que la
 * emisión guardaría.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDraftPreviewDTO {
    private List<InvoiceItemDTO> items;
    private List<InvoiceOrderDTO> orders;
    private String customerName;
    private String customerRtn;
    /** Paciente de la factura cuando todas sus órdenes son de uno solo. */
    private String patientName;
    private BigDecimal subtotal;
    private BigDecimal itemDiscountAmount;
    /** Null cuando las órdenes mezclan tramos de edad. */
    private AgeDiscountKind discountKind;
    private String discountLabel;
    private BigDecimal discountPercent;
    private BigDecimal discountAmount;
    private BigDecimal otherDiscountAmount;
    private BigDecimal total;
}
