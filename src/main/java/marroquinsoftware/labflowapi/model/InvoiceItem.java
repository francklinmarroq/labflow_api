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

    @Column(name = "test_id")
    private Long testId;

    @Column(nullable = false)
    private String testName;

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

    /** Lo que realmente se le cobra al paciente por esta línea. */
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    /** Precio de lista con respaldo para las facturas viejas sin snapshot. */
    public BigDecimal listPriceOrPrice() {
        return listPrice != null ? listPrice : price;
    }
}
