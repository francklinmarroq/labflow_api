package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.AgeDiscountKind;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que costaría facturar una orden hoy: los exámenes con el precio vigente
 * del catálogo y el descuento por edad que aplica. Nada queda guardado; los
 * montos definitivos se congelan al emitir. Si la orden ya tiene una factura
 * viva, viene referenciada para que la pantalla redirija en vez de emitir.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoicePreviewDTO {
    private Long orderId;
    private Long orderNumber;
    private Long customerId;
    private String customerName;
    /** RTN del expediente del paciente, como sugerencia editable. */
    private String customerRtn;
    private AgeDiscountKind discountKind;
    private String discountLabel;
    private BigDecimal discountPercent;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal total;
    private List<InvoiceItemDTO> items;
    /** Factura viva ya emitida para esta orden, si existe. */
    private Long existingInvoiceId;
    private String existingInvoiceNumber;
}
