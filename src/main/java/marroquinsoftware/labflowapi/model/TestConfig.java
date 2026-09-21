package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.TenantId;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_test_config_name_per_lab", columnNames = {"laboratory_id", "name"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "test_id", nullable = false)
    private Test test;

    @NotBlank
    private String name;

    // Los parámetros del perfil con su orden. Cada fila guarda display_order en
    // la tabla de unión; @OrderBy hace que se lean ya ordenados, que es el orden
    // que respeta el reporte al imprimir.
    // El historial del paciente arma la curva de varios perfiles a la vez y
    // recorre sus parámetros; sin el @BatchSize sería una consulta por perfil al
    // inicializar esta colección lazy (N+1). El @OrderBy se conserva.
    @OneToMany(mappedBy = "testConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder")
    @BatchSize(size = 50)
    private List<TestConfigParameter> configParameters = new ArrayList<>();

    // Los métodos (técnicas) con los que el laboratorio corre este examen. Colección
    // propia del perfil, igual que configParameters: se guardan y se borran con él.
    // La pantalla de órdenes lee los métodos del perfil que ya tiene cacheado, así
    // que se leen por lote (@BatchSize) y ya ordenados por nombre, que es como se
    // ofrecen al elegir. Cuál es el predeterminado va como booleano en la fila del
    // método (TestMethod.defaultMethod) y NO como una asociación desde acá: sería un
    // ciclo EAGER leído en cada pantalla de órdenes (ver design.md).
    @OneToMany(mappedBy = "testConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    @BatchSize(size = 50)
    private List<TestMethod> methods = new ArrayList<>();

    private boolean active;

    // Presentacion del perfil en el reporte. NONE = solo tabla (por defecto).
    // LINE = ademas se grafica una curva con los valores de los parametros, usando
    // el chartXValue de cada uno como eje X (ej. curva de glucosa/insulina).
    @Enumerated(EnumType.STRING)
    @Column(name = "chart_type")
    private ChartType chartType = ChartType.NONE;

    // Etiqueta del eje X cuando chartType = LINE (ej. "Tiempo (min)").
    @Column(name = "chart_x_axis_label")
    private String chartXAxisLabel;

    // Como se listan los resultados en el reporte. STANDARD = tabla por parametro
    // agrupada por seccion (por defecto). ANTIBIOGRAM = se agrupan por su valor de
    // sensibilidad (Sensible/Intermedio/Resistente), como en un antibiograma.
    @Enumerated(EnumType.STRING)
    @Column(name = "result_layout")
    private ResultLayout resultLayout = ResultLayout.STANDARD;

    // Independiente de resultLayout: permite adjuntar foto(s)/escaneo del reporte
    // que ya imprime el equipo (p. ej. un hemograma automatizado) ademas de -no en
    // vez de- la captura de parametros. Un TestRun puede traer resultados,
    // adjuntos, o ambos.
    @Column(name = "allow_result_attachments")
    private boolean allowResultAttachments;
}
