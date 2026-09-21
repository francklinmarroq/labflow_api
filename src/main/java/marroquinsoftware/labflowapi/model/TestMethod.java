package marroquinsoftware.labflowapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.TenantId;

/**
 * Técnica con la que el laboratorio corre un examen (ELISA, quimioluminiscencia,
 * aglutinación). Pertenece al PERFIL del examen ({@link TestConfig}), que es donde
 * ya vive todo lo demás sobre cómo se mide y se presenta ese examen, y no a la
 * orden: antes se reescribía a mano en cada orden y se imprimía en el reporte tal
 * como se hubiera tecleado esa vez.
 *
 * <p>No es un catálogo que haya que dar de alta antes: el método nace la primera
 * vez que alguien lo escribe en una orden y a partir de ahí el perfil lo ofrece
 * (ver {@code TestMethodService.resolveOrCreate}). Es el mismo patrón de
 * {@link OrderTag} y {@link ReferringPhysician}, pero acotado al perfil: el mismo
 * nombre bajo dos perfiles son DOS métodos, de dos exámenes distintos, y renombrar
 * uno no toca al otro.
 *
 * <p>Por eso guarda dos nombres: {@code name} es como se escribió y como se
 * imprime, y {@code normalizedName} es la llave de comparación (sin espacios de
 * más, sin tildes y en minúsculas) para que "Quimioluminiscencia",
 * "quimioluminiscencia" y " Quimioluminiscencia " sean el MISMO método y no tres.
 * La unicidad es POR PERFIL.
 *
 * <p>{@code defaultMethod} marca cuál de los métodos del perfil es el
 * predeterminado: el que se le estampa a un examen nuevo al asignarle el perfil.
 * Es un booleano en la fila del método y no una llave foránea en el perfil a
 * propósito — un {@code TestConfig.defaultMethod} sería un ciclo EAGER
 * (perfil → método → perfil) leído en cada pantalla de órdenes; ver design.md del
 * cambio test-method-catalog. El "a lo sumo uno verdadero por perfil" lo sostiene
 * el servicio, que es el único que lo escribe.
 */
@Entity
@Table(name = "test_methods", uniqueConstraints = @UniqueConstraint(
        name = "uk_test_method_name_per_config",
        columnNames = {"test_config_id", "normalized_name"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    /**
     * El perfil dueño del método. Se excluye de toString/equals porque el perfil
     * tiene de vuelta la colección de sus métodos: incluirlo sería recursión
     * infinita en cuanto alguien imprima o compare cualquiera de los dos.
     */
    @ManyToOne
    @JoinColumn(name = "test_config_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TestConfig testConfig;

    /** Nombre visible, tal como se escribió la primera vez (o como se corrigió). */
    @Column(nullable = false, length = 255)
    private String name;

    /** Llave de comparación: {@code name} sin tildes, sin espacios de más y en minúsculas. */
    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    /**
     * ¿Es el método predeterminado de su perfil? A lo sumo uno por perfil, y puede
     * no haber ninguno. Se llama {@code defaultMethod} y no {@code isDefault}
     * porque esto último dejaría la propiedad con el nombre {@code default}, que es
     * palabra reservada tanto en SQL como en JPQL.
     */
    @Column(name = "is_default", nullable = false)
    private boolean defaultMethod;
}
