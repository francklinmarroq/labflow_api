package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.TestConfig;
import marroquinsoftware.labflowapi.model.TestMethod;
import marroquinsoftware.labflowapi.payload.LabTestDTO;
import marroquinsoftware.labflowapi.payload.TestMethodDTO;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.TestConfigRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import marroquinsoftware.labflowapi.service.LabTestService;
import marroquinsoftware.labflowapi.service.LabTestServiceImp;
import marroquinsoftware.labflowapi.service.TestMethodService;
import marroquinsoftware.labflowapi.service.TestMethodServiceImp;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import marroquinsoftware.labflowapi.tenant.TenantIdentifierResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El método del examen de una orden: se escribe por NOMBRE, el que el perfil no
 * tenga se le agrega, elegirlo lo deja de predeterminado del perfil, y al asignar el
 * perfil a un examen nuevo el predeterminado se le estampa.
 *
 * <p>La línea que separa las dos mitades del cambio: lo que una orden dice que se
 * usó NO cambia por una decisión de configuración posterior. Estampar es lo que
 * hace inofensivo cambiar el predeterminado más adelante.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({LabTestServiceImp.class, TestMethodServiceImp.class, TenantIdentifierResolver.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class OrderTestMethodTest {

    @PersistenceContext EntityManager entityManager;

    @Autowired LabTestService labTestService;
    @Autowired TestMethodService testMethodService;
    @Autowired TestConfigRepository testConfigRepository;
    @Autowired TestRepository testRepository;
    @Autowired LabTestRepository labTestRepository;
    @Autowired LabOrderRepository labOrderRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired LaboratoryRepository laboratoryRepository;

    private Customer customer;
    private marroquinsoftware.labflowapi.model.Test vih;
    private TestConfig perfilVih;

    @BeforeEach
    void setUp() {
        Laboratory laboratory = new Laboratory();
        laboratory.setName("Laboratorio de Prueba");
        laboratory = laboratoryRepository.save(laboratory);
        TenantContext.setLaboratoryId(laboratory.getId());

        customer = new Customer();
        customer.setName("Paciente de Prueba");
        customer.setAgeInDays(30 * 365);
        customer = customerRepository.save(customer);

        vih = newTest("VIH");
        perfilVih = newConfig(vih, "Perfil VIH");
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    // --- Utilidades ---

    private marroquinsoftware.labflowapi.model.Test newTest(String name) {
        marroquinsoftware.labflowapi.model.Test test = new marroquinsoftware.labflowapi.model.Test();
        test.setName(name);
        return testRepository.save(test);
    }

    private TestConfig newConfig(marroquinsoftware.labflowapi.model.Test test, String name) {
        TestConfig config = new TestConfig();
        config.setTest(test);
        config.setName(name);
        config.setActive(true);
        return testConfigRepository.save(config);
    }

    /** Un examen recién agregado a una orden: sin perfil, como lo deja addTestToOrder. */
    private LabTest newLabTestWithoutConfig(marroquinsoftware.labflowapi.model.Test test) {
        LabOrder order = new LabOrder();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order = labOrderRepository.save(order);

        LabTest labTest = new LabTest();
        labTest.setOrder(order);
        labTest.setTest(test);
        labTest.setTestConfig(null);
        return labTestRepository.save(labTest);
    }

    /** El examen con su perfil ya asignado, que es como lo deja el frontend enseguida. */
    private LabTest newLabTestWithConfig(TestConfig config) {
        LabTest labTest = newLabTestWithoutConfig(config.getTest());
        labTestService.assignTestConfig(labTest.getOrder().getId(), labTest.getId(), config.getId());
        entityManager.flush();
        entityManager.clear();
        return labTestRepository.findById(labTest.getId()).orElseThrow();
    }

    private TestConfig reread(Long configId) {
        entityManager.flush();
        entityManager.clear();
        return testConfigRepository.findById(configId).orElseThrow();
    }

    private String defaultNameOf(TestConfig config) {
        return config.getMethods().stream()
                .filter(TestMethod::isDefaultMethod)
                .map(TestMethod::getName)
                .findFirst().orElse(null);
    }

    // --- Elegir el método en la orden ---

    // El caso que motiva la feature: se escribe la técnica en la orden, sin haber
    // configurado nada antes, y a partir de ahí el perfil la ofrece y la trae puesta.
    @Test
    void settingAMethodByNameLinksItAndMakesItTheTemplateDefault() {
        LabTest labTest = newLabTestWithConfig(perfilVih);

        LabTestDTO dto = labTestService.updateMethod(labTest.getOrder().getId(), labTest.getId(), "ELISA");

        assertEquals("ELISA", dto.getMethod());
        assertNotNull(dto.getMethodId(), "el examen reporta también cuál de los métodos del perfil es");
        TestConfig stored = reread(perfilVih.getId());
        assertEquals(1, stored.getMethods().size(), "el nombre que el perfil no tenía se le agrega");
        assertEquals("ELISA", defaultNameOf(stored), "el último elegido queda de predeterminado");
    }

    // Elegir distinto en una orden posterior cambia lo que arrancan las SIGUIENTES,
    // sin tocar las órdenes ya levantadas.
    @Test
    void choosingADifferentMethodMovesTheDefaultWithoutTouchingOrdersAlreadyTaken() {
        LabTest first = newLabTestWithConfig(perfilVih);
        labTestService.updateMethod(first.getOrder().getId(), first.getId(), "ELISA");
        LabTest second = newLabTestWithConfig(perfilVih);

        labTestService.updateMethod(second.getOrder().getId(), second.getId(), "Quimioluminiscencia");

        assertEquals("Quimioluminiscencia", defaultNameOf(reread(perfilVih.getId())));
        assertEquals("ELISA",
                labTestRepository.findById(first.getId()).orElseThrow().getMethod().getName(),
                "la orden anterior sigue diciendo lo que dijo");
    }

    // Sin perfil no hay de dónde elegir ni dónde guardar uno nuevo: se rechaza
    // diciendo qué falta, en vez de descartar el método en silencio.
    @Test
    void settingTheMethodOnAnExamWithNoTemplateIsRefusedAndChangesNothing() {
        LabTest labTest = newLabTestWithoutConfig(vih);

        APIException error = assertThrows(APIException.class, () ->
                labTestService.updateMethod(labTest.getOrder().getId(), labTest.getId(), "ELISA"));

        assertTrue(error.getMessage().toLowerCase().contains("perfil"), error.getMessage());
        entityManager.clear();
        assertNull(labTestRepository.findById(labTest.getId()).orElseThrow().getMethod());
        assertTrue(reread(perfilVih.getId()).getMethods().isEmpty(), "no se creó nada en ningún perfil");
    }

    // Un examen sin perfil se LEE sin problema: es un estado pasajero, no un error.
    @Test
    void anExamWithNoTemplateReportsNoMethodAndNoError() {
        LabTest labTest = newLabTestWithoutConfig(vih);

        List<LabTestDTO> tests = labTestService.getTestsByOrder(labTest.getOrder().getId());

        assertEquals(1, tests.size());
        assertNull(tests.get(0).getMethod());
        assertNull(tests.get(0).getMethodId());
    }

    // Dejar de indicar la técnica no dice nada sobre cuál es la usual.
    @Test
    void aBlankNameClearsTheExamWithoutTouchingTheTemplateDefault() {
        LabTest labTest = newLabTestWithConfig(perfilVih);
        labTestService.updateMethod(labTest.getOrder().getId(), labTest.getId(), "ELISA");

        LabTestDTO cleared = labTestService.updateMethod(labTest.getOrder().getId(), labTest.getId(), "  ");

        assertNull(cleared.getMethod());
        assertNull(cleared.getMethodId());
        TestConfig stored = reread(perfilVih.getId());
        assertEquals(1, stored.getMethods().size(), "el método sigue en el perfil");
        assertEquals("ELISA", defaultNameOf(stored), "y sigue siendo el predeterminado");
    }

    // --- Estampar el predeterminado al asignar el perfil ---

    @Test
    void assigningATemplateStampsItsDefaultOntoAnExamWithNoMethod() {
        testMethodService.reconcile(perfilVih, List.of(new TestMethodDTO(null, "ELISA", true)));
        entityManager.flush();
        entityManager.clear();
        LabTest labTest = newLabTestWithoutConfig(vih);

        LabTestDTO dto = labTestService.assignTestConfig(
                labTest.getOrder().getId(), labTest.getId(), perfilVih.getId());

        assertEquals("ELISA", dto.getMethod(),
                "quien abre la orden lo encuentra ya puesto, sin escribirlo cada vez");
    }

    @Test
    void assigningATemplateWithNoDefaultLeavesTheExamWithoutOne() {
        testMethodService.reconcile(perfilVih, List.of(new TestMethodDTO(null, "ELISA", false)));
        entityManager.flush();
        entityManager.clear();
        LabTest labTest = newLabTestWithoutConfig(vih);

        LabTestDTO dto = labTestService.assignTestConfig(
                labTest.getOrder().getId(), labTest.getId(), perfilVih.getId());

        assertNull(dto.getMethod());
    }

    // Cambiar de perfil no puede reescribir en silencio lo que el técnico indicó.
    @Test
    void assigningDoesNotOverwriteAnExamThatAlreadyNamesAMethod() {
        LabTest labTest = newLabTestWithConfig(perfilVih);
        labTestService.updateMethod(labTest.getOrder().getId(), labTest.getId(), "Aglutinación");
        // Otro perfil del mismo examen, con OTRO predeterminado.
        TestConfig otro = newConfig(vih, "Perfil VIH confirmatorio");
        testMethodService.reconcile(otro, List.of(new TestMethodDTO(null, "Western Blot", true)));
        entityManager.flush();
        entityManager.clear();

        LabTestDTO dto = labTestService.assignTestConfig(
                labTest.getOrder().getId(), labTest.getId(), otro.getId());

        assertEquals("Aglutinación", dto.getMethod(), "el examen conserva el método que ya nombraba");
    }

    // Lo que hace inofensivo cambiar el predeterminado más adelante.
    @Test
    void changingTheDefaultLaterDoesNotTouchExamsAlreadyStamped() {
        testMethodService.reconcile(perfilVih, List.of(new TestMethodDTO(null, "ELISA", true)));
        entityManager.flush();
        entityManager.clear();
        LabTest labTest = newLabTestWithConfig(perfilVih);
        assertEquals("ELISA", labTest.getMethod().getName());

        TestConfig config = reread(perfilVih.getId());
        testMethodService.reconcile(config, List.of(
                new TestMethodDTO(config.getMethods().get(0).getId(), "ELISA", false),
                new TestMethodDTO(null, "Quimioluminiscencia", true)));
        entityManager.flush();
        entityManager.clear();

        assertEquals("ELISA", labTestRepository.findById(labTest.getId()).orElseThrow().getMethod().getName());
        assertEquals("Quimioluminiscencia", defaultNameOf(reread(perfilVih.getId())));
    }

    // --- Lo que el examen reporta ---

    // El nombre reportado es el VIGENTE del perfil: por eso corregirlo ahí corrige
    // también las órdenes ya levantadas y sus reportes.
    @Test
    void anExamReportsTheCurrentNameAfterARename() {
        LabTest labTest = newLabTestWithConfig(perfilVih);
        labTestService.updateMethod(labTest.getOrder().getId(), labTest.getId(), "quimioluminicencia");

        TestConfig config = reread(perfilVih.getId());
        testMethodService.reconcile(config, List.of(
                new TestMethodDTO(config.getMethods().get(0).getId(), "Quimioluminiscencia", true)));
        entityManager.flush();
        entityManager.clear();

        LabTestDTO dto = labTestService.getTestsByOrder(labTest.getOrder().getId()).get(0);
        assertEquals("Quimioluminiscencia", dto.getMethod());
    }

    // methodId es solo lectura: lo que se escribe es el nombre.
    @Test
    void aMethodIdSentOnAWriteIsIgnored() {
        LabTest existing = newLabTestWithConfig(perfilVih);
        labTestService.updateMethod(existing.getOrder().getId(), existing.getId(), "ELISA");
        entityManager.flush();
        entityManager.clear();
        Long elisaId = reread(perfilVih.getId()).getMethods().get(0).getId();

        // Un cliente manda el id del método al agregar otro examen a la orden.
        LabTestDTO request = new LabTestDTO();
        request.setTestId(vih.getId());
        request.setMethodId(elisaId);
        request.setMethod("ELISA");

        LabTestDTO created = labTestService.addTestToOrder(existing.getOrder().getId(), request);

        assertNull(created.getMethodId(), "el id enviado no engancha nada");
        assertNull(created.getMethod(), "el examen nace sin método, como siempre");
    }
}
