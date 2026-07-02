package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
    @OneToMany(mappedBy = "testConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder")
    private List<TestConfigParameter> configParameters = new ArrayList<>();

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
}
