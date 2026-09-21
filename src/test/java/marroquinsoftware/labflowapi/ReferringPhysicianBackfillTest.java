package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import marroquinsoftware.labflowapi.config.ReferringPhysicianBackfill;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.service.InvoiceService;
import marroquinsoftware.labflowapi.service.LabOrderService;
import marroquinsoftware.labflowapi.service.LabOrderServiceImp;
import marroquinsoftware.labflowapi.service.OrderTagServiceImp;
import marroquinsoftware.labflowapi.service.ReferringPhysicianServiceImp;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migración de los médicos que se escribieron como texto libre antes de que
 * existiera el catálogo: cada nombre distinto por laboratorio pasa a ser una ficha
 * y las órdenes quedan enganchadas a ella.
 *
 * <p>La columna vieja ya no la mapea ninguna entidad, así que con
 * {@code ddl-auto=create-drop} no existe: cada prueba la agrega a mano, que es
 * justo el estado del que parte una base de producción.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({LabOrderServiceImp.class, OrderTagServiceImp.class, ReferringPhysicianServiceImp.class,
        TenantIdentifierResolver.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class ReferringPhysicianBackfillTest {

    @MockitoBean InvoiceService invoiceService;

    @PersistenceContext EntityManager entityManager;

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired LabOrderService labOrderService;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired CustomerRepository customerRepository;

    private Customer customer;
    private ReferringPhysicianBackfill backfill;

    /**
     * La columna se agrega UNA sola vez para toda la clase, antes de crear nada.
     * Un DDL hace commit implícito de la transacción en curso, así que agregarla y
     * soltarla en cada prueba rompería el rollback del {@code @DataJpaTest} y las
     * órdenes de una prueba sobrevivirían a la siguiente (chocando por el folio).
     */
    private static boolean legacyColumnAdded;

    @BeforeEach
    void setUp() {
        if (!legacyColumnAdded) {
            // La columna de texto libre tal como está en una base anterior al cambio.
            jdbcTemplate.execute("alter table lab_orders add column referring_physician varchar(150)");
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

        backfill = new ReferringPhysicianBackfill(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Long newOrderWithLegacyPhysician(String legacyName) {
        LabOrderDTO dto = new LabOrderDTO();
        dto.setCustomerId(customer.getId());
        dto.setStatus(OrderStatus.PENDING);
        LabOrderDTO created = labOrderService.createOrder(dto);
        // Las órdenes nacen sin médico (el catálogo aún no existía) y el texto se
        // pone directo en la columna vieja, que es como están en producción.
        entityManager.flush();
        if (legacyName != null) {
            jdbcTemplate.update("update lab_orders set referring_physician = ? where id = ?",
                    legacyName, created.getId());
        }
        return created.getId();
    }

    private List<Map<String, Object>> physicians() {
        return jdbcTemplate.queryForList(
                "select id, laboratory_id, name, normalized_name from referring_physicians order by id");
    }

    private Long physicianIdOf(Long orderId) {
        return jdbcTemplate.queryForObject(
                "select referring_physician_id from lab_orders where id = ?", Long.class, orderId);
    }

    // El caso central: los nombres ya tecleados pasan al catálogo y las órdenes
    // quedan enganchadas, sin que nadie los vuelva a escribir.
    @Test
    void namesTypedBeforeTheCatalogueEndUpInIt() {
        Long first = newOrderWithLegacyPhysician("Dra. Ana Fúnez");
        Long second = newOrderWithLegacyPhysician("Dr. Carlos Mejía");
        entityManager.flush();

        backfill.run();
        entityManager.clear();

        assertEquals(2, physicians().size());
        assertNotNull(physicianIdOf(first));
        assertNotNull(physicianIdOf(second));
        assertNotEquals(physicianIdOf(first), physicianIdOf(second));
    }

    // Dos escrituras del mismo médico son UNA ficha: si no, el catálogo arrancaría
    // con las mismas variantes que la columna de texto tenía.
    //
    // Ojo con lo que "la misma escritura" significa: la llave de comparación ignora
    // tildes, mayúsculas y espacios de más, pero NO la puntuación — "Dra. Ana Fúnez"
    // y "Dra Ana Funez" (sin el punto) son dos médicos distintos, igual que pasa con
    // las etiquetas. Por eso las dos variantes de acá conservan el punto.
    @Test
    void twoSpellingsOfOnePhysicianCollapseToOneEntry() {
        Long first = newOrderWithLegacyPhysician("Dra. Ana Fúnez");
        Long second = newOrderWithLegacyPhysician("dra.  ana funez");
        entityManager.flush();

        backfill.run();
        entityManager.clear();

        assertEquals(1, physicians().size());
        assertEquals(physicianIdOf(first), physicianIdOf(second));
        // Se conserva una de las escrituras reales: la primera que se encontró.
        assertEquals("Dra. Ana Fúnez", physicians().get(0).get("name"));
    }

    @Test
    void ordersWithNoPhysicianStayWithoutOne() {
        Long withNone = newOrderWithLegacyPhysician(null);
        Long withBlank = newOrderWithLegacyPhysician("   ");
        entityManager.flush();

        backfill.run();
        entityManager.clear();

        assertTrue(physicians().isEmpty());
        assertNull(physicianIdOf(withNone));
        assertNull(physicianIdOf(withBlank));
    }

    // Idempotente por construcción: solo mira las órdenes sin enganchar, así que un
    // segundo arranque no crea fichas ni reengancha nada.
    @Test
    void runningAgainChangesNothing() {
        Long order = newOrderWithLegacyPhysician("Dra. Ana Fúnez");
        entityManager.flush();

        backfill.run();
        entityManager.clear();
        Long afterFirst = physicianIdOf(order);

        backfill.run();
        entityManager.clear();

        assertEquals(1, physicians().size(), "no se crea una segunda ficha");
        assertEquals(afterFirst, physicianIdOf(order), "la orden sigue enganchada a la misma");
    }

    // El agrupamiento respeta el laboratorio: el mismo nombre en dos laboratorios da
    // dos fichas, nunca una compartida.
    @Test
    void theSameNameInTwoLaboratoriesGivesOneEntryEach() {
        Long mine = newOrderWithLegacyPhysician("Dra. Ana Fúnez");
        Long theirs = newOrderWithLegacyPhysician("Dra. Ana Fúnez");
        entityManager.flush();

        Laboratory other = new Laboratory();
        other.setName("Otro Laboratorio");
        other = laboratoryRepository.save(other);
        // Se mueve una orden al otro laboratorio con SQL nativo: el backfill lee el
        // laboratory_id de la fila, que es lo que se quiere comprobar.
        jdbcTemplate.update("update lab_orders set laboratory_id = ? where id = ?", other.getId(), theirs);
        entityManager.clear();

        backfill.run();

        List<Map<String, Object>> created = physicians();
        assertEquals(2, created.size(), "una ficha por laboratorio");
        assertNotEquals(physicianIdOf(mine), physicianIdOf(theirs));
        assertNotEquals(created.get(0).get("laboratory_id"), created.get(1).get("laboratory_id"));
    }

    // Después del último paso de la migración (el drop de la columna) el backfill no
    // tiene nada que leer: debe salirse en silencio y no dejar un warning por arranque.
    @Test
    void withoutTheLegacyColumnItDoesNothing() {
        jdbcTemplate.execute("alter table lab_orders drop column referring_physician");

        assertDoesNotThrow(() -> backfill.run());
        assertTrue(physicians().isEmpty());

        // Se repone para que el tearDown la pueda soltar como en las demás pruebas.
        jdbcTemplate.execute("alter table lab_orders add column referring_physician varchar(150)");
    }
}
