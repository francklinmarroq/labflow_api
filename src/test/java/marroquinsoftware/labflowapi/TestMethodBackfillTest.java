package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import marroquinsoftware.labflowapi.config.TestMethodBackfill;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.TestConfig;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.TestConfigRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import marroquinsoftware.labflowapi.tenant.TenantIdentifierResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migración de los métodos que se escribieron como texto libre en cada examen de
 * cada orden antes de que los perfiles los tuvieran: cada nombre distinto por perfil
 * pasa a ser un método del perfil y los exámenes quedan enganchados a él.
 *
 * <p>La columna vieja ya no la mapea ninguna entidad, así que con
 * {@code ddl-auto=create-drop} no existe: la prueba la agrega a mano, que es justo el
 * estado del que parte una base de producción.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({TenantIdentifierResolver.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class TestMethodBackfillTest {

    @PersistenceContext EntityManager entityManager;

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TestConfigRepository testConfigRepository;
    @Autowired TestRepository testRepository;
    @Autowired LabTestRepository labTestRepository;
    @Autowired LabOrderRepository labOrderRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired LaboratoryRepository laboratoryRepository;

    private Customer customer;
    private TestMethodBackfill backfill;

    /**
     * La columna se agrega UNA sola vez para toda la clase, antes de crear nada. Un
     * DDL hace commit implícito de la transacción en curso, así que agregarla y
     * soltarla en cada prueba rompería el rollback del {@code @DataJpaTest} y los
     * datos de una prueba sobrevivirían a la siguiente.
     */
    private static boolean legacyColumnAdded;

    @BeforeEach
    void setUp() {
        if (!legacyColumnAdded) {
            // La columna de texto libre tal como está en una base anterior al cambio.
            jdbcTemplate.execute("alter table lab_tests add column method varchar(255)");
            legacyColumnAdded = true;
        }

        Laboratory laboratory = new Laboratory();
        laboratory.setName("Laboratorio de Prueba");
        laboratory = laboratoryRepository.save(laboratory);
        TenantContext.setLaboratoryId(laboratory.getId());

        customer = new Customer();
        customer.setName("Paciente de Prueba");
        customer.setAgeInDays(30 * 365);
        customer = customerRepository.save(customer);

        backfill = new TestMethodBackfill(jdbcTemplate);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    // --- Utilidades ---

    private TestConfig newConfig(String name) {
        marroquinsoftware.labflowapi.model.Test test = new marroquinsoftware.labflowapi.model.Test();
        test.setName(name);
        test = testRepository.save(test);

        TestConfig config = new TestConfig();
        config.setTest(test);
        config.setName("Perfil de " + name);
        config.setActive(true);
        return testConfigRepository.save(config);
    }

    /**
     * Un examen como los de producción antes del cambio: con el método escrito en la
     * columna de texto y sin enganche al perfil.
     */
    private Long newLabTestWithLegacyMethod(TestConfig config, String legacyMethod) {
        LabOrder order = new LabOrder();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order = labOrderRepository.save(order);

        LabTest labTest = new LabTest();
        labTest.setOrder(order);
        labTest.setTest(config != null ? config.getTest() : anyTest());
        labTest.setTestConfig(config);
        labTest = labTestRepository.save(labTest);

        entityManager.flush();
        if (legacyMethod != null) {
            jdbcTemplate.update("update lab_tests set method = ? where id = ?", legacyMethod, labTest.getId());
        }
        return labTest.getId();
    }

    private marroquinsoftware.labflowapi.model.Test anyTest() {
        marroquinsoftware.labflowapi.model.Test test = new marroquinsoftware.labflowapi.model.Test();
        test.setName("Examen sin perfil " + System.nanoTime());
        return testRepository.save(test);
    }

    private List<Map<String, Object>> methods() {
        return jdbcTemplate.queryForList(
                "select id, laboratory_id, test_config_id, name, normalized_name, is_default "
                        + "from test_methods order by id");
    }

    private Long methodIdOf(Long labTestId) {
        return jdbcTemplate.queryForObject("select method_id from lab_tests where id = ?", Long.class, labTestId);
    }

    private String legacyMethodOf(Long labTestId) {
        return jdbcTemplate.queryForObject("select method from lab_tests where id = ?", String.class, labTestId);
    }

    // --- Las pruebas ---

    // El caso central: los métodos ya tecleados pasan al perfil y los exámenes quedan
    // enganchados, sin que nadie los vuelva a escribir.
    @Test
    void methodsTypedBeforeTheTemplatesHeldThemEndUpOnTheTemplate() {
        TestConfig config = newConfig("VIH");
        Long first = newLabTestWithLegacyMethod(config, "ELISA");
        Long second = newLabTestWithLegacyMethod(config, "Aglutinación");
        entityManager.flush();

        backfill.run();
        entityManager.clear();

        assertEquals(2, methods().size());
        assertNotNull(methodIdOf(first));
        assertNotNull(methodIdOf(second));
        assertNotEquals(methodIdOf(first), methodIdOf(second));
    }

    // Dos escrituras del mismo método son UN método: si no, el perfil arrancaría con
    // las mismas variantes que la columna de texto tenía.
    @Test
    void twoSpellingsOfOneMethodCollapseToOne() {
        TestConfig config = newConfig("Toxoplasma");
        Long first = newLabTestWithLegacyMethod(config, "Quimioluminiscencia");
        Long second = newLabTestWithLegacyMethod(config, "  quimioluminiscencia ");
        entityManager.flush();

        backfill.run();
        entityManager.clear();

        assertEquals(1, methods().size());
        assertEquals(methodIdOf(first), methodIdOf(second));
        // Se conserva una de las escrituras reales: la primera que se encontró.
        assertEquals("Quimioluminiscencia", methods().get(0).get("name"));
    }

    // El más reciente queda de predeterminado: es el mejor sustituto disponible de
    // "el que se usó la última vez".
    @Test
    void theMostRecentMethodBecomesTheTemplateDefault() {
        TestConfig config = newConfig("VIH");
        newLabTestWithLegacyMethod(config, "ELISA");
        newLabTestWithLegacyMethod(config, "elisa");
        Long latest = newLabTestWithLegacyMethod(config, "Quimioluminiscencia");
        entityManager.flush();

        backfill.run();
        entityManager.clear();

        assertEquals(2, methods().size(), "las dos escrituras de ELISA son un solo método");
        List<Map<String, Object>> isDefault = methods().stream()
                .filter(m -> Boolean.TRUE.equals(m.get("is_default"))).toList();
        assertEquals(1, isDefault.size());
        assertEquals("Quimioluminiscencia", isDefault.get(0).get("name"));
        assertEquals(methodIdOf(latest), ((Number) isDefault.get(0).get("id")).longValue());
    }

    // El perfil no se adivina: un examen sin perfil se deja exacto como está. Son los
    // que hay que resolver a mano antes de soltar la columna vieja.
    @Test
    void anExamWithNoTemplateIsLeftUntouched() {
        TestConfig config = newConfig("VIH");
        Long withConfig = newLabTestWithLegacyMethod(config, "ELISA");
        Long withoutConfig = newLabTestWithLegacyMethod(null, "Citometría de flujo");
        entityManager.flush();

        backfill.run();
        entityManager.clear();

        assertEquals(1, methods().size(), "no se inventa un método para el examen sin perfil");
        assertNotNull(methodIdOf(withConfig));
        assertNull(methodIdOf(withoutConfig), "queda sin enganchar");
        assertEquals("Citometría de flujo", legacyMethodOf(withoutConfig), "y conserva su texto");
    }

    @Test
    void examsWithNoMethodStayWithoutOne() {
        TestConfig config = newConfig("Hemograma");
        Long withNone = newLabTestWithLegacyMethod(config, null);
        Long withBlank = newLabTestWithLegacyMethod(config, "   ");
        entityManager.flush();

        backfill.run();
        entityManager.clear();

        assertTrue(methods().isEmpty());
        assertNull(methodIdOf(withNone));
        assertNull(methodIdOf(withBlank));
    }

    // Idempotente por construcción: solo mira los exámenes sin enganchar, así que un
    // segundo arranque no crea métodos, no reengancha nada y no mueve el predeterminado.
    @Test
    void runningAgainChangesNothing() {
        TestConfig config = newConfig("VIH");
        Long first = newLabTestWithLegacyMethod(config, "ELISA");
        Long second = newLabTestWithLegacyMethod(config, "Quimioluminiscencia");
        entityManager.flush();

        backfill.run();
        entityManager.clear();
        List<Map<String, Object>> afterFirstRun = methods();
        Long firstMethod = methodIdOf(first);
        Long secondMethod = methodIdOf(second);

        backfill.run();
        entityManager.clear();

        assertEquals(afterFirstRun, methods(), "ni un método nuevo ni un predeterminado distinto");
        assertEquals(firstMethod, methodIdOf(first));
        assertEquals(secondMethod, methodIdOf(second));
    }

    // Después del último paso de la migración (el drop de la columna) el backfill no
    // tiene nada que leer: debe salirse en silencio y no dejar un warning por arranque.
    @Test
    void withoutTheLegacyColumnItDoesNothing() {
        jdbcTemplate.execute("alter table lab_tests drop column method");

        assertDoesNotThrow(() -> backfill.run());
        assertTrue(methods().isEmpty());

        // Se repone para las demás pruebas de la clase.
        jdbcTemplate.execute("alter table lab_tests add column method varchar(255)");
    }
}
