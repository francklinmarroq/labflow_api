package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

/**
 * Una orden incluida en una factura. Es la única fuente de "qué órdenes cubre
 * esta factura" y de "qué factura cubre esta orden": el bloqueo de exámenes, la
 * vista de la orden, la doble facturación y los filtros por orden y etiqueta
 * leen de aquí. No vive en las líneas porque una línea agrupada ("10 —
 * Hemograma") junta exámenes de varias órdenes.
 *
 * <p>Congela lo que la factura necesita de cada orden: el paciente (para que la
 * factura diga de quién son los exámenes) y su descuento por edad, que se
 * calcula por orden según su propio paciente.
 */
@Entity
@Table(name = "invoice_orders", indexes = {
        @Index(name = "ix_invoice_orders_invoice", columnList = "invoice_id"),
        @Index(name = "ix_invoice_orders_order", columnList = "order_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "invoice_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Invoice invoice;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LabOrder order;

    /** Paciente de la orden al emitir. */
    @ManyToOne
    @JoinColumn(name = "customer_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Customer customer;

    /** Nombre del paciente congelado al emitir: lo que imprime la factura. */
    @Column(name = "patient_name")
    private String patientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_discount_kind")
    private AgeDiscountKind ageDiscountKind;

    /** Porcentaje del tramo de edad del paciente (0 si no aplica). */
    @Column(name = "age_percent", precision = 5, scale = 2)
    private BigDecimal agePercent;

    /** Lo que se cobra por los exámenes de esta orden, antes del descuento por edad. */
    @Column(name = "charged_amount", precision = 12, scale = 2)
    private BigDecimal chargedAmount;

    /** Descuento por edad que la regla da a esta orden. */
    @Column(name = "age_discount_amount", precision = 12, scale = 2)
    private BigDecimal ageDiscountAmount;
}
