package marroquinsoftware.labflowapi.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Clave compuesta de la tabla de union test_config_parameters: un parametro
 * aparece una sola vez por perfil. Coincide con la PK (test_config_id,
 * parameter_id) que ya existia, por lo que no hay que reestructurar la tabla.
 */
public class TestConfigParameterId implements Serializable {

    private Long testConfig;
    private Long parameter;

    public TestConfigParameterId() {
    }

    public TestConfigParameterId(Long testConfig, Long parameter) {
        this.testConfig = testConfig;
        this.parameter = parameter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestConfigParameterId that)) return false;
        return Objects.equals(testConfig, that.testConfig)
                && Objects.equals(parameter, that.parameter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testConfig, parameter);
    }
}
