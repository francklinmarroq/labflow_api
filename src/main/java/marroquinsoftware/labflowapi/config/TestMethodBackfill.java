package marroquinsoftware.labflowapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pasa a los perfiles los métodos que se escribieron como texto libre en cada
 * examen de cada orden antes de que los perfiles los tuvieran (lab_tests.method).
 * Por cada perfil crea un método por nombre distinto y engancha los exámenes que lo
 * tenían escrito, de modo que el perfil arranca con las técnicas que el laboratorio
 * ya venía usando y nadie tiene que volver a teclearlas.
 *
 * <p>Dos nombres son el MISMO método si coinciden sin tildes, sin espacios de más y
 * en minúsculas — la misma regla de {@code TestMethodServiceImp}. Se normaliza en
 * Java y no en SQL a propósito: es la única forma de garantizar que la comparación
 * sea idéntica a la del servicio, y es portable entre PostgreSQL (producción) y H2
 * (pruebas); {@code unaccent} es una extensión de Postgres que puede no estar
 * instalada y que en H2 no existe.
 *
 * <p>El predeterminado de cada perfil queda siendo el método del examen de id más
 * alto de ese perfil: el examen creado más recientemente, que es el mejor sustituto
 * disponible de "el que se usó la última vez". Solo se marca si el perfil aún no
 * tiene predeterminado, para no pisar una decisión ya tomada.
 *
 * <p>Los exámenes cuyo perfil no se puede determinar (test_config_id nulo) se dejan
 * EXACTOS como están y se cuentan en el log. No se adivina el perfil a partir del
 * examen: acertaría casi siempre, y el caso en que no, le atribuye una técnica a un
 * perfil que nunca la corrió y además se la deja de predeterminada. Ese conteo es lo
 * que habilita el último paso de la migración (el drop de la columna vieja,
 * comentado en schema.sql): mientras no sea cero, la columna es la única copia de
 * esos métodos.
 *
 * <p>Va con SQL nativo por id para saltarse el filtro de {@code @TenantId} y tocar
 * los exámenes de TODOS los laboratorios (el agrupamiento sí queda por laboratorio
 * solo: un perfil pertenece a uno). Es idempotente por construcción: solo mira los
 * exámenes con method_id nulo, así que en arranques posteriores no hace nada.
 */
@Component
public class TestMethodBackfill implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestMethodBackfill.class);

    private static final int MAX_NAME_LENGTH = 255;

    private final JdbcTemplate jdbcTemplate;

    public TestMethodBackfill(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            // La columna vieja ya no la mapea ninguna entidad, así que en una base
            // nueva nunca llega a existir, y en las viejas desaparece cuando se corre
            // el último paso de la migración (el drop comentado en schema.sql). En
            // ambos casos no hay nada que pasar a los perfiles: se sale en silencio,
            // sin dejar un warning en cada arranque.
            if (!legacyColumnExists()) {
                return;
            }
            // Ordenados por id: el último que se procesa de cada perfil es el examen
            // más reciente, que es el que decide el predeterminado.
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select id, laboratory_id, test_config_id, method from lab_tests "
                            + "where method is not null and method_id is null order by id");
            if (rows.isEmpty()) {
                return;
            }

            // Métodos que ya están en los perfiles, para no duplicar los que se hayan
            // creado por el camino normal (o en una corrida anterior a medias).
            Map<String, Long> byConfigAndName = new HashMap<>();
            for (Map<String, Object> existing : jdbcTemplate.queryForList(
                    "select id, test_config_id, normalized_name from test_methods")) {
                byConfigAndName.put(key(existing.get("test_config_id"), (String) existing.get("normalized_name")),
                        ((Number) existing.get("id")).longValue());
            }

            int linked = 0;
            int unresolved = 0;
            int created = 0;
            // Último método visto por perfil: como las filas vienen por id ascendente,
            // al terminar cada entrada es la del examen más reciente de ese perfil.
            Map<Long, Long> lastMethodByConfig = new LinkedHashMap<>();

            for (Map<String, Object> row : rows) {
                String raw = (String) row.get("method");
                if (raw == null || raw.isBlank()) continue;
                Object testConfigId = row.get("test_config_id");
                if (testConfigId == null) {
                    // Sin perfil no hay dónde poner el método. Se deja el texto como
                    // está y se cuenta: lo resuelve una persona asignando el perfil.
                    unresolved++;
                    continue;
                }
                String name = cleanName(raw);
                String normalized = normalize(name);
                String key = key(testConfigId, normalized);

                Long methodId = byConfigAndName.get(key);
                if (methodId == null) {
                    jdbcTemplate.update(
                            "insert into test_methods (laboratory_id, test_config_id, name, normalized_name, is_default) "
                                    + "values (?, ?, ?, ?, ?)",
                            row.get("laboratory_id"), testConfigId, name, normalized, false);
                    methodId = jdbcTemplate.queryForObject(
                            "select id from test_methods where test_config_id = ? and normalized_name = ?",
                            Long.class, testConfigId, normalized);
                    byConfigAndName.put(key, methodId);
                    created++;
                }
                jdbcTemplate.update("update lab_tests set method_id = ? where id = ?",
                        methodId, ((Number) row.get("id")).longValue());
                linked++;
                lastMethodByConfig.put(((Number) testConfigId).longValue(), methodId);
            }

            for (Map.Entry<Long, Long> entry : lastMethodByConfig.entrySet()) {
                // Solo si el perfil todavía no tiene predeterminado: si alguien ya
                // eligió uno (o lo eligió una corrida anterior), no se le pisa.
                Integer alreadyDefault = jdbcTemplate.queryForObject(
                        "select count(*) from test_methods where test_config_id = ? and is_default = true",
                        Integer.class, entry.getKey());
                if (alreadyDefault != null && alreadyDefault == 0) {
                    jdbcTemplate.update("update test_methods set is_default = true where id = ?", entry.getValue());
                }
            }

            if (linked > 0) {
                log.info("Backfill de métodos de examen: {} exámenes enganchados a {} métodos nuevos de perfil.",
                        linked, created);
            }
            if (unresolved > 0) {
                // Este renglón es el que gobierna el último paso de la migración: no se
                // suelta la columna vieja mientras no diga cero.
                log.warn("Backfill de métodos de examen: {} exámenes con método escrito y SIN perfil asignado "
                        + "quedaron sin migrar. Conservan su texto en lab_tests.method; asígneles el perfil y "
                        + "vuelva a fijarles el método. NO suelte la columna lab_tests.method hasta que esto sea cero.",
                        unresolved);
            }
        } catch (Exception e) {
            // No debe tumbar el arranque; si las columnas o la tabla aún no existen
            // (primera vez, antes de correr schema.sql) simplemente no hay nada que
            // pasar a los perfiles y se reintenta en el siguiente arranque.
            log.warn("No se pudo ejecutar el backfill de métodos de examen: {}", e.getMessage());
        }
    }

    /**
     * ¿Sigue existiendo la columna de texto libre? Se consulta information_schema
     * comparando en minúsculas porque H2 guarda los identificadores sin comillas en
     * mayúsculas y PostgreSQL en minúsculas.
     */
    private boolean legacyColumnExists() {
        Integer found = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where lower(table_name) = 'lab_tests' and lower(column_name) = 'method'",
                Integer.class);
        return found != null && found > 0;
    }

    /** Agrupa por perfil: el mismo nombre en dos perfiles son dos métodos. */
    private String key(Object testConfigId, String normalizedName) {
        return testConfigId + " " + normalizedName;
    }

    /** Recorta, colapsa espacios repetidos y corta al largo de la columna. */
    private String cleanName(String raw) {
        String name = raw.trim().replaceAll("\\s+", " ");
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    /** Misma llave de comparación que TestMethodServiceImp: NFD, sin diacríticos, minúsculas. */
    private String normalize(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase();
    }
}
