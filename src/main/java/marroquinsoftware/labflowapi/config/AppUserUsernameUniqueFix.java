package marroquinsoftware.labflowapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drift de restricción UNIQUE en app_user al pasar a "un correo, varios
 * laboratorios": antes {@code username} era @Column(unique=true) (unique GLOBAL);
 * ahora la unicidad es por laboratorio (uk_app_user_username_per_lab, sobre
 * username + laboratory_id). Con ddl-auto=update Hibernate AGREGA la restricción
 * nueva pero NUNCA elimina la vieja, así que en bases previas sobrevive el unique
 * global e impide crear una segunda fila con el mismo correo (romperia todo el
 * feature con "violates unique constraint").
 *
 * <p>No se dropea en schema.sql porque el nombre del constraint viejo es un hash
 * determinístico que genera Hibernate y no lo conocemos de antemano; aquí se
 * busca por introspección (pg_constraint) el UNIQUE de UNA sola columna sobre
 * {@code username} y se elimina. Es idempotente: si ya no existe, no hace nada.
 * En H2 (tests) el schema lo crea Hibernate desde la entidad ya con el unique
 * compuesto, así que la consulta a pg_constraint falla y se ignora sin efecto.
 */
@Component
public class AppUserUsernameUniqueFix implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AppUserUsernameUniqueFix.class);

    private final JdbcTemplate jdbcTemplate;

    public AppUserUsernameUniqueFix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            // UNIQUE de una sola columna sobre app_user(username). El compuesto
            // (username, laboratory_id) tiene conkey de largo 2 y queda excluido.
            List<String> names = jdbcTemplate.queryForList(
                    "select con.conname " +
                            "from pg_constraint con " +
                            "join pg_class rel on rel.oid = con.conrelid " +
                            "where rel.relname = 'app_user' " +
                            "  and con.contype = 'u' " +
                            "  and array_length(con.conkey, 1) = 1 " +
                            "  and (select attname from pg_attribute " +
                            "       where attrelid = con.conrelid and attnum = con.conkey[1]) = 'username'",
                    String.class);
            for (String name : names) {
                // El nombre viene de pg_catalog, no de entrada del usuario: seguro
                // interpolarlo (ALTER TABLE no acepta el nombre como parámetro).
                jdbcTemplate.execute("alter table app_user drop constraint if exists \"" + name + "\"");
                log.info("Eliminado unique global viejo de app_user.username: {}", name);
            }
        } catch (Exception e) {
            // No debe tumbar el arranque: en H2 no existe pg_constraint y en bases
            // nuevas el unique global nunca existió.
            log.warn("No se pudo revisar/eliminar el unique global de app_user.username: {}", e.getMessage());
        }
    }
}
