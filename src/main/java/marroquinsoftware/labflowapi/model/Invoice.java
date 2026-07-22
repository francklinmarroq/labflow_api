package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Factura CAI (SAR Honduras) emitida desde una orden. Es un documento fiscal:
 * todo lo que se imprime queda congelado al emitir — el número con su CAI,
 * rango autorizado y fecha límite, los datos del emisor, el cliente, y el
 * nombre y precio de cada examen con el descuento por edad vigente. Nunca se
 * borra: se anula, y la anulación genera el contra-asiento contable.
 *
 * <p>Los servicios de laboratorio están exentos de ISV, así que el importe
 * exento impreso es el total; no se guardan tasas por línea.
 */
@Entity
@Table(name = "invoices", uniqueConstraints = @UniqueConstraint(
        name = "uk_invoice_number_per_lab",
        columnNames = {"laboratory_id", "invoice_number"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número fiscal completo, ej. "000-001-01-00000042". */
    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    // Snapshot del CAI con el que se emitió (para reimprimir aunque cambie la
    // configuración del laboratorio).
    @Column(nullable = false)
    private String cai;
    private String caiRangeFrom;
    private String caiRangeTo;
    private LocalDate caiExpirationDate;

    // Snapshot del emisor (membrete fiscal).
    private String labName;
    private String labRtn;
    private String labAddress;
    private String labPhone;
    private String labHeadline;
    private String labFooterNote;
    private String labPacNumber;
    private String labRegExonerado;
    private String labRegSag;
    private String labOrdenCompraExenta;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private LabOrder order;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Nombre del cliente con el que se emitió (copiado del expediente). */
    @Column(nullable = false)
    private String customerName;

    /** RTN del cliente cuando pidió factura con RTN; null = consumidor final. */
    private String customerRtn;

    private Instant issuedAt;
    private String issuedByUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SaleCondition saleCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgeDiscountKind discountKind;

    /** Porcentaje aplicado (0–100) según la configuración vigente al facturar. */
    @Column(precision = 5, scale = 2)
    private BigDecimal discountPercent;

    /** Bruto: suma de los precios de lista de las líneas, sin ningún descuento. */
    @Column(precision = 12, scale = 2)
    private BigDecimal subtotal;

    /**
     * Parte del descuento atribuible al tramo de edad. Si al final se rebajó
     * menos que lo que dictaba la regla (pasa: el paciente califica al 20% pero
     * se le dio 10%), acá queda lo que realmente se rebajó por ese concepto y no
     * el monto teórico — así las líneas impresas siempre suman el total.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal discountAmount;

    /**
     * Rebaja adicional a la de edad: promociones, cortesías o un precio de
     * cierre negociado en mostrador. Sale de la diferencia entre lo que daba el
     * cálculo automático y el total que el operador decidió cobrar.
     *
     * <p>Nullable por las facturas anteriores a este campo, que no tenían forma
     * de llevar otros descuentos; ahí equivale a cero.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal otherDiscountAmount;

    /** Lo que se le cobra al paciente, ya con todos los descuentos aplicados. */
    @Column(precision = 12, scale = 2)
    private BigDecimal total;

    /** Suma de los pagos activos; el saldo es total - paidAmount. */
    @Column(precision = 12, scale = 2)
    private BigDecimal paidAmount;

    private Instant annulledAt;
    private String annulledByUsername;
    private String annulmentReason;

    // El listado devuelve cada factura con sus líneas; sin el @BatchSize eso
    // sería una consulta por factura al recorrer la página.
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<InvoiceItem> items;
}
