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
public class ExpenseDTO {
    private Long id;
    private LocalDate expenseDate;
    private String description;
    private BigDecimal amount;
    private Long expenseAccountId;
    private String expenseAccountCode;
    private String expenseAccountName;
    private PaymentMethod method;
    /** Etiqueta lista para mostrar de la forma de pago (ej. "Efectivo"). */
    private String methodLabel;
    private String createdByUsername;
    private Instant createdAt;
    private boolean annulled;
    private Instant annulledAt;
    private String annulledByUsername;
    private String annulmentReason;
}
