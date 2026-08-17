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
 * Etiqueta libre con la que el laboratorio clasifica sus órdenes: convenios
 * ("IHSS", "Seguro X"), campañas, empresas, jornadas… Sirve para filtrar después
 * las órdenes y las facturas por ese criterio.
 *
 * <p>No es un catálogo que haya que dar de alta antes: la etiqueta se crea sola
 * la primera vez que alguien la escribe en una orden y a partir de ahí queda
 * disponible para reutilizarla (ver {@code OrderTagService.resolveOrCreate}).
 *
 * <p>Por eso guarda dos nombres: {@code name} es como se escribió y como se
 * muestra, y {@code normalizedName} es la llave de comparación (sin espacios de
 * más, sin tildes y en minúsculas) para que "IHSS", "ihss" e " Ihss " sean la
 * MISMA etiqueta y no tres. La unicidad es por laboratorio.
 */
@Entity
@Table(name = "order_tags", uniqueConstraints = @UniqueConstraint(
        name = "uk_order_tag_name_per_lab",
        columnNames = {"laboratory_id", "normalized_name"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    /** Nombre visible, tal como se escribió la primera vez (o como se renombró). */
    @Column(nullable = false, length = 60)
    private String name;

    /** Llave de comparación: {@code name} sin tildes, sin espacios de más y en minúsculas. */
    @Column(name = "normalized_name", nullable = false, length = 60)
    private String normalizedName;

    /**
     * Color del distintivo en la interfaz, en hexadecimal ({@code #1d4ed8}).
     * Opcional: sin él, el frontend pinta la etiqueta con el color neutro.
     */
    @Column(length = 7)
    private String color;
}
