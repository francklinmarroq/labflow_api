package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPaymentDTO {
    private Long id;
    private Long paymentNumber;
    private Long purchaseId;
    private LocalDate paymentDate;
    private BigDecimal amount;
    private PaymentMethod method;
    private String methodLabel;
    private String reference;
    private Instant createdAt;
    private String createdByUsername;
    private boolean annulled;
    private Instant annulledAt;
    private String annulledByUsername;
    private String annulmentReason;
}
