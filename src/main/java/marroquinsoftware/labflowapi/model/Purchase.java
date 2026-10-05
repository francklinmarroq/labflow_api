package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Documento de compra: la factura (o recibo) de un proveedor con su
 * información fiscal y sus líneas. Es inmutable, como un gasto: postea su
 * asiento al registrarse y un error se corrige anulándolo con contra-asiento.
 *
 * <p>El desglose fiscal (bases por tasa e ISV) lo calcula el servidor a partir
 * de las líneas y se guarda en el documento, que es de donde lo lee el libro de
 * compras: el mayor solo sabe que el ISV fue a "ISV no recuperable".
 */
@Entity
@Table(name = "purchases", indexes = {
        @Index(name = "ix_purchases_date", columnList = "laboratory_id, purchase_date"),
        @Index(name = "ix_purchases_supplier", columnList = "supplier_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /** Fecha de emisión de la factura del proveedor; es también la del asiento. */
    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    /** Número fiscal del documento del proveedor (ej. 000-001-01-00001234). */
    @Column(nullable = false)
    private String fiscalNumber;

    /** CAI del proveedor; opcional porque no todo documento de compra lo trae. */
    private String cai;

    /** Fecha límite de emisión del CAI del proveedor. */
    private LocalDate caiDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_condition", nullable = false)
    private SaleCondition condition;

    /** Forma de pago de una compra de contado; null en las compras a crédito. */
    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal exemptBase;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxedBase15;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxedBase18;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal isv15;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal isv18;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /** Suma de los pagos vigentes; en una de contado, el total desde que nace. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseStatus status;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder")
    @BatchSize(size = 50)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<PurchaseLine> lines;

    private Instant createdAt;
    private String createdByUsername;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean annulled;

    private Instant annulledAt;
    private String annulledByUsername;
    private String annulmentReason;
}
