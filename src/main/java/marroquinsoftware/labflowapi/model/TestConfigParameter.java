package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Fila de la tabla de union entre un perfil (TestConfig) y un parametro.
 *
 * Se modela como entidad propia (en vez de un @ManyToMany simple) para guardar
 * el orden de cada parametro dentro del perfil en la columna display_order. El
 * orden se respeta al leer mediante @OrderBy en TestConfig, y es el que usa el
 * reporte al imprimir.
 *
 * display_order es nullable a proposito: las filas creadas antes de existir el
 * ordenamiento quedan en NULL sin romper la lectura (ordenan al final).
 */
@Entity
@Table(name = "test_config_parameters")
@IdClass(TestConfigParameterId.class)
@Getter
@Setter
@NoArgsConstructor
public class TestConfigParameter {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_config_id")
    private TestConfig testConfig;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id")
    private Parameter parameter;

    @Column(name = "display_order")
    private Integer displayOrder;

    // Posicion del parametro en el eje X del grafico cuando el perfil es una curva
    // (TestConfig.chartType = LINE). Ej. 0, 30, 60, 120 (minutos). nullable: solo
    // tiene sentido en perfiles de curva; en el resto queda en NULL.
    @Column(name = "chart_x_value")
    private BigDecimal chartXValue;

    public TestConfigParameter(TestConfig testConfig, Parameter parameter, Integer displayOrder) {
        this.testConfig = testConfig;
        this.parameter = parameter;
        this.displayOrder = displayOrder;
    }

    public TestConfigParameter(TestConfig testConfig, Parameter parameter, Integer displayOrder, BigDecimal chartXValue) {
        this.testConfig = testConfig;
        this.parameter = parameter;
        this.displayOrder = displayOrder;
        this.chartXValue = chartXValue;
    }
}
