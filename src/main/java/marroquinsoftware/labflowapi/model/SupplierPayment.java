package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Pago a un proveedor contra una compra a crédito: espejo del abono a una
 * factura de venta. Lleva su propio correlativo de recibo por laboratorio
 * ({@link SupplierPaymentCounter}) y se anula con contra-asiento.
 */
@Entity
@Table(name = "supplier_payments", uniqueConstraints = @UniqueConstraint(
        name = "uk_supplier_payment_number_per_lab", columnNames = {"laboratory_id", "payment_number"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_number")
    private Long paymentNumber;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "purchase_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Purchase purchase;

    /** Fecha del pago; es también la del asiento. */
    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    /** Número de cheque, transferencia o similar; opcional. */
    private String reference;

    private Instant createdAt;
    private String createdByUsername;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean annulled;

    private Instant annulledAt;
    private String annulledByUsername;
    private String annulmentReason;
}
