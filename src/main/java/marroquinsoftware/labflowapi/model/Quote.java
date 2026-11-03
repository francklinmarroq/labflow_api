package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.TenantId;

/**
 * Cotización de exámenes para un paciente: lo que se le dice al paciente que va
 * a costar antes de crear la orden.
 *
 * <p>Es un documento de precios, así que todo lo que se imprime queda congelado
 * al crearla: el nombre y la edad del paciente, el nombre y precio de cada
 * examen, y el descuento por edad con el porcentaje vigente en ese momento. Si
 * después cambian los precios del catálogo o la configuración de descuentos, la
 * cotización ya emitida no se mueve.
 *
 * <p>El paciente puede ser uno del expediente ({@code customer}) o alguien que
 * todavía no está registrado, del que solo se pidió nombre y edad para saber si
 * cae en el descuento.
 *
 * <p>El estado (vigente, vencida, convertida, recotizada) no se guarda: sale de
 * {@code validUntil} y de los vínculos a la orden o a la cotización que la
 * reemplazó. Ver {@link QuoteStatus#of(Quote)}.
 */
@Entity
@Table(name = "quotes", uniqueConstraints = @UniqueConstraint(
        name = "uk_quote_number_per_lab",
        columnNames = {"laboratory_id", "quote_number"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Correlativo visible por laboratorio, asignado desde {@link QuoteCounter}. */
    @Column(name = "quote_number")
    private Long quoteNumber;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    /** Paciente del expediente, si la cotización se hizo sobre uno existente. */
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Nombre con el que se emitió (copiado del paciente o escrito a mano). */
    @Column(nullable = false)
    private String patientName;

    /** Edad con la que se calculó el descuento, en días (misma unidad que Customer). */
    private Integer patientAgeInDays;

    private Instant quotedAt;

    @Column(length = 2000)
    private String notes;

    /** Usuario que emitió la cotización. */
    private String createdByUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgeDiscountKind discountKind;

    /** Porcentaje aplicado (0–100) según la configuración vigente al cotizar. */
    @Column(precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal total;

    // El listado devuelve cada cotización con sus exámenes; sin el @BatchSize
    // eso sería una consulta por cotización al recorrer la página.
    /** Días de vigencia con que se emitió (o los que se fijaron al editarla). */
    private Integer validityDays;

    /**
     * Último día (hora de Honduras) en que se respetan los precios cotizados:
     * fecha de emisión + {@code validityDays}.
     */
    private LocalDate validUntil;

    // Orden creada a partir de esta cotización. Id y número planos, sin FK, como
    // QuoteItem.testId: solo se muestran y el número de una orden nunca cambia.
    private Long orderId;
    private Long orderNumber;

    /** Cotización nueva que reemplazó a esta al recotizarla. */
    private Long requotedById;
    private Long requotedByNumber;

    /** Cotización vencida de la que salió esta al recotizar. */
    private Long requotedFromId;
    private Long requotedFromNumber;

    /** Última modificación (null si nunca se editó). */
    private Instant updatedAt;
    private String updatedByUsername;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<QuoteItem> items;
}
