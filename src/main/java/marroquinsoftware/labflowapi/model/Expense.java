package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Gasto del laboratorio. Al registrarlo se genera su partida en el diario
 * (debe: la cuenta de gasto elegida; haber: Caja o Bancos según la forma de
 * pago). Los gastos no se editan: un error se corrige anulando el gasto (lo
 * que genera el contra-asiento) y registrando uno nuevo.
 */
@Entity
@Table(name = "expenses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Cuenta de tipo GASTO a la que se carga. */
    @ManyToOne
    @JoinColumn(name = "expense_account_id", nullable = false)
    private Account expenseAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    private String createdByUsername;

    private Instant createdAt;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean annulled;

    private Instant annulledAt;
    private String annulledByUsername;
    private String annulmentReason;
}
