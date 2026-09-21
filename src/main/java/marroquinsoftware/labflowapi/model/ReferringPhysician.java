package marroquinsoftware.labflowapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

/**
 * Médico que refiere trabajo al laboratorio: quien solicita la orden y a quien se
 * le atribuye en el reporte impreso.
 *
 * <p>No es un catálogo que haya que dar de alta antes: el médico se crea solo la
 * primera vez que alguien escribe su nombre en una orden y a partir de ahí queda
 * disponible para reutilizarlo (ver {@code ReferringPhysicianService.resolveOrCreate}).
 * Es el mismo patrón de {@link OrderTag}, sin el color.
 *
 * <p>Por eso guarda dos nombres: {@code name} es como se escribió y como se
 * imprime, y {@code normalizedName} es la llave de comparación (sin espacios de
 * más, sin tildes y en minúsculas) para que "Dra. Ana Fúnez", "dra. ana funez" y
 * " Dra.  Ana Funez " sean el MISMO médico y no tres. La unicidad es por
 * laboratorio.
 */
@Entity
@Table(name = "referring_physicians", uniqueConstraints = @UniqueConstraint(
        name = "uk_referring_physician_name_per_lab",
        columnNames = {"laboratory_id", "normalized_name"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferringPhysician {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    /** Nombre visible, tal como se escribió la primera vez (o como se corrigió). */
    @Column(nullable = false, length = 150)
    private String name;

    /** Llave de comparación: {@code name} sin tildes, sin espacios de más y en minúsculas. */
    @Column(name = "normalized_name", nullable = false, length = 150)
    private String normalizedName;
}
