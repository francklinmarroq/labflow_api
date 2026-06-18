package marroquinsoftware.labflowapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Rellena la columna display_order de la tabla de union test_config_parameters
 * para los perfiles que ya existian antes de introducir el ordenamiento.
 *
 * Esas filas se crearon sin orden (la relacion era un Set) y, tras agregar
 * @OrderColumn, quedan con display_order = NULL. Hibernate no puede materializar
 * una lista ordenada con indices nulos, por lo que sin este relleno la lectura
 * de perfiles falla. La sentencia es idempotente: solo toca filas con NULL, asi
 * que en arranques posteriores no actualiza nada.
 */
@Component
public class TestConfigOrderBackfill implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestConfigOrderBackfill.class);

    private final JdbcTemplate jdbcTemplate;

    public TestConfigOrderBackfill(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            // Asigna un orden 0..n-1 por perfil a las filas sin orden, usando el
            // id del parametro para que el resultado sea estable. El usuario puede
            // luego reacomodarlas con arrastre desde el front.
            int updated = jdbcTemplate.update("""
                    UPDATE test_config_parameters t
                    SET display_order = sub.rn
                    FROM (
                        SELECT test_config_id,
                               parameter_id,
                               row_number() OVER (
                                   PARTITION BY test_config_id ORDER BY parameter_id
                               ) - 1 AS rn
                        FROM test_config_parameters
                        WHERE display_order IS NULL
                    ) sub
                    WHERE t.test_config_id = sub.test_config_id
                      AND t.parameter_id = sub.parameter_id
                      AND t.display_order IS NULL
                    """);
            if (updated > 0) {
                log.info("Backfill de display_order en test_config_parameters: {} filas actualizadas.", updated);
            }
        } catch (Exception e) {
            // No debe tumbar el arranque; si la tabla aun no existe (primer arranque
            // sin datos) simplemente no hay nada que rellenar.
            log.warn("No se pudo ejecutar el backfill de display_order: {}", e.getMessage());
        }
    }
}
