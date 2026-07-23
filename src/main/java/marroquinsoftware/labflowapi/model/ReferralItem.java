package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.TenantId;

/**
 * Examen concreto remitido dentro de una {@link Referral}. Guarda el
 * {@code testName} como snapshot para que la boleta quede íntegra aunque luego
 * se quite el examen de la orden; {@code labTestId} es una referencia simple
 * (sin FK) al {@link LabTest} de origen.
 */
@Entity
@Table(name = "referral_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferralItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "referral_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Referral referral;

    @Column(name = "lab_test_id")
    private Long labTestId;

    @Column(nullable = false)
    private String testName;

    /**
     * Lo que el laboratorio de destino cobra por realizar esta prueba (costo del
     * examen remitido). Puede ser 0 en la rara ocasión en que no cobran.
     */
    @Column(precision = 12, scale = 2)
    private java.math.BigDecimal cost;
}
