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
 * Examen cotizado dentro de una {@link Quote}. Guarda nombre y precio como
 * snapshot para que la cotización impresa siga cuadrando aunque el catálogo
 * cambie; {@code testId} es una referencia simple (sin FK) al examen de origen.
 */
@Entity
@Table(name = "quote_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "quote_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Quote quote;

    @Column(name = "test_id")
    private Long testId;

    @Column(nullable = false)
    private String testName;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;
}
