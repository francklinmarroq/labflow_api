package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.TenantId;

import java.util.List;

@Entity
@Table(name = "lab_tests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private LabOrder order;

    @ManyToOne
    @JoinColumn(name = "test_id", nullable = false)
    private Test test;

    @ManyToOne
    @JoinColumn(name = "test_config_id", nullable = true)
    private TestConfig testConfig;

    @Column(length = 2000)
    private String notes;

    // Datos opcionales del examen que solo se imprimen si se establecen (ej. en /ordenes/[id])
    @Column(length = 255)
    private String sampleType;

    /**
     * Método/técnica con la que se corrió este examen (ej. ELISA,
     * Quimioluminiscencia). Opcional, y solo se imprime en el reporte si está.
     *
     * <p>El método NO es texto de esta fila: pertenece al PERFIL del examen
     * ({@link TestConfig#getMethods()}), que es donde vive todo lo demás sobre cómo
     * se corre y se presenta ese examen. Acá solo se apunta a cuál de los métodos
     * del perfil se usó, de modo que corregir cómo se escribe una técnica en el
     * perfil corrige también las órdenes ya levantadas y sus reportes.
     *
     * <p>Se escribe y se lee por NOMBRE (ver {@code LabTestDTO.method}): un nombre
     * que el perfil no tenga se agrega al perfil y se usa, y elegirlo lo deja como
     * el predeterminado del perfil. Por eso un examen sin perfil asignado todavía no
     * acepta método: no hay de dónde elegir ni dónde guardar uno nuevo.
     *
     * <p>EAGER como toda asociación a-uno de este modelo: la imagen nativa no puede
     * fabricar el proxy de una perezosa (ver NativeImageLazyAssociationTest).
     */
    @ManyToOne
    @JoinColumn(name = "method_id")
    private TestMethod method;

    // El detalle de la orden y el historial del paciente recorren las corridas de
    // varios exámenes a la vez; sin el @BatchSize sería una consulta por examen al
    // inicializar esta colección lazy (N+1).
    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<TestRun> runs;
}
