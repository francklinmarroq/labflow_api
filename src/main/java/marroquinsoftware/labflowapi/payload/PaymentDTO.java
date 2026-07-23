package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDTO {
    private Long id;
    private Long paymentNumber;
    private Instant paidAt;
    private BigDecimal amount;
    private PaymentMethod method;
    private String methodLabel;
    private String reference;
    private String receivedByUsername;
    private boolean annulled;
    private Instant annulledAt;
    private String annulledByUsername;
    private String annulmentReason;
}
