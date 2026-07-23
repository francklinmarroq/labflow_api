package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferralDTO {
    private Long id;
    private Long orderId;
    private Long orderNumber;
    private String destinationLabName;
    private String reason;
    private Instant referredAt;
    private String createdByUsername;
    /** Suma de los costos de los exámenes remitidos. */
    private BigDecimal totalCost;
    /** null = quedó por pagar; con método = se pagó al momento. */
    private PaymentMethod paymentMethod;
    /** Texto para mostrar: "Por pagar" o "Pagado (Efectivo)". */
    private String settlementLabel;
    private List<ReferralItemDTO> items;
}
