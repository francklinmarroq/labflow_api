package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Una orden pendiente de facturar, para elegirla al armar una factura. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UninvoicedOrderDTO {
    private Long orderId;
    private Long orderNumber;
    private Instant requestedAt;
    private Long customerId;
    private String customerName;
    private int testCount;
    /** Suma de los exámenes a precio de catálogo, antes de descuentos. */
    private BigDecimal catalogTotal;
}
