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

/**
 * Línea de una factura: snapshot del examen al momento de emitir (igual que
 * {@link QuoteItem}). El {@code testId} es informativo, sin FK, para que borrar
 * un examen del catálogo no toque facturas ya emitidas.
 */
@Entity
@Table(name = "invoice_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItem {

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

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'EXAMEN'")
    @Column(name = "item_type", nullable = false, length = 20)
    private InvoiceItemType itemType = InvoiceItemType.EXAMEN;

    /** Examen del catálogo; null en un concepto libre. */
    @Column(name = "test_id")
    private Long testId;

    /** Nombre del examen, o la descripción del concepto, congelado al emitir. */
    @Column(nullable = false)
    private String testName;

    /**
     * Unidades de la línea. Los exámenes iguales de la factura se agrupan en una
     * sola línea con su cantidad; las líneas anteriores a este campo valen 1.
     * {@code listPrice} y {@code price} son unitarios.
     */
    @ColumnDefault("1")
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity = BigDecimal.ONE;

    /**
     * Precio de lista del catálogo al emitir. Se guarda aparte de {@code price}
     * para que una regalía o un precio especial quede visible en la factura:
     * la línea muestra cuánto costaba y cuánto se cobró.
     *
     * <p>Nullable por las facturas emitidas antes de existir este campo; ahí se
     * asume que no hubo ajuste y vale lo mismo que {@code price}.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal listPrice;

    /** Lo que realmente se cobra por cada unidad de esta línea. */
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    /** Precio de lista con respaldo para las facturas viejas sin snapshot. */
    public BigDecimal listPriceOrPrice() {
        return listPrice != null ? listPrice : price;
    }

    /** Importe cobrado de la línea: precio unitario × cantidad. */
    public BigDecimal amount() {
        BigDecimal qty = quantity != null ? quantity : BigDecimal.ONE;
        return price.multiply(qty).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Importe de lista de la línea: precio de catálogo × cantidad. */
    public BigDecimal listAmount() {
        BigDecimal qty = quantity != null ? quantity : BigDecimal.ONE;
        return listPriceOrPrice().multiply(qty).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
