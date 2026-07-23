package marroquinsoftware.labflowapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Rellena lab_orders.public_token en las órdenes creadas antes de existir el
 * enlace público de resultados. Esas filas quedan con public_token = NULL; sin
 * token no se puede armar su URL/QR ni abrir su reporte público.
 *
 * <p>Se genera el UUID en Java (no con una función SQL) para que sea portable
 * entre Postgres (prod) y H2 (tests), y con SQL nativo por id para saltarse el
 * filtro de @TenantId y tocar las órdenes de todos los laboratorios. Es
 * idempotente: en arranques posteriores ya no hay filas con NULL y no hace nada.
 */
@Component
public class PublicTokenBackfill implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PublicTokenBackfill.class);

    private final JdbcTemplate jdbcTemplate;

    public PublicTokenBackfill(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "select id from lab_orders where public_token is null", Long.class);
            for (Long id : ids) {
                jdbcTemplate.update("update lab_orders set public_token = ? where id = ?",
                        UUID.randomUUID().toString(), id);
            }
            if (!ids.isEmpty()) {
                log.info("Backfill de public_token en lab_orders: {} órdenes actualizadas.", ids.size());
            }
        } catch (Exception e) {
            // No debe tumbar el arranque; si la tabla aún no existe (primer arranque
            // sin datos) simplemente no hay nada que rellenar.
            log.warn("No se pudo ejecutar el backfill de public_token: {}", e.getMessage());
        }
    }
}
