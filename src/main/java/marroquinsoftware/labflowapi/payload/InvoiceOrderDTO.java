package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.AgeDiscountKind;

import java.math.BigDecimal;

/** Una orden incluida en una factura, con su paciente y su descuento por edad. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceOrderDTO {
    private Long orderId;
    private Long orderNumber;
    private Long customerId;
    private String patientName;
    private AgeDiscountKind ageDiscountKind;
    private String ageDiscountLabel;
    private BigDecimal agePercent;
    /** Lo que se cobra por los exámenes de esta orden, antes del descuento por edad. */
    private BigDecimal chargedAmount;
    private BigDecimal ageDiscountAmount;
}
