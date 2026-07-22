package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pago (abono) de una factura. Cada pago genera su partida contable
 * (Caja/Bancos contra Cuentas por cobrar) y su recibo con correlativo propio.
 * Los pagos no se editan: se anulan con contra-asiento.
 */
@Entity
@Table(name = "invoice_payments", uniqueConstraints = @UniqueConstraint(
        name = "uk_payment_number_per_lab",
        columnNames = {"laboratory_id", "payment_number"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Correlativo visible del recibo por laboratorio, de {@link PaymentCounter}. */
    @Column(name = "payment_number")
    private Long paymentNumber;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "invoice_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Invoice invoice;

    private Instant paidAt;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    /** Referencia del voucher o número de transferencia, si aplica. */
    private String reference;

    private String receivedByUsername;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean annulled;

    private Instant annulledAt;
    private String annulledByUsername;
    private String annulmentReason;
}
