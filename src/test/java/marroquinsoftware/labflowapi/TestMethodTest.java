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
import marroquinsoftware.labflowapi.payload.TestMethodDTO;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.TestConfigRepository;
import marroquinsoftware.labflowapi.repositories.TestMethodRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Los métodos (técnicas) del perfil de un examen: nacen la primera vez que se
 * escriben, se reutilizan después aunque se escriban distinto, el último elegido
 * queda de predeterminado, renombrar uno corrige las órdenes que ya lo indicaban y
 * quitar uno que alguna orden indique se rechaza.
 *
 * <p>La unicidad es POR PERFIL, no por laboratorio: el mismo nombre bajo dos
 * exámenes son dos técnicas distintas, de dos exámenes distintos, y administrar una
 * no puede tocar a la otra.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({TestMethodServiceImp.class, TenantIdentifierResolver.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class TestMethodTest {

    @PersistenceContext EntityManager entityManager;

    @Autowired TestMethodService testMethodService;
    @Autowired TestMethodRepository testMethodRepository;
    @Autowired TestConfigRepository testConfigRepository;
    @Autowired TestRepository testRepository;
    @Autowired LabTestRepository labTestRepository;
    @Autowired LabOrderRepository labOrderRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired LaboratoryRepository laboratoryRepository;

    private Customer customer;

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

    /** Un examen de una orden que indica ese método, como los que bloquean el borrado. */
    private LabTest newLabTestUsing(TestConfig config, TestMethod method) {
        LabOrder order = new LabOrder();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order = labOrderRepository.save(order);

        LabTest labTest = new LabTest();
        labTest.setOrder(order);
        labTest.setTest(config.getTest());
        labTest.setTestConfig(config);
        labTest.setMethod(method);
        return labTestRepository.save(labTest);
    }

    private TestMethodDTO row(Long id, String name, boolean isDefault) {
        return new TestMethodDTO(id, name, isDefault);
    }

    /** Vuelve a leer el perfil desde la base, sin la caché de la sesión. */
    private TestConfig reread(Long configId) {
        entityManager.flush();
        entityManager.clear();
        return testConfigRepository.findById(configId).orElseThrow();
    }

    // --- resolveOrCreate: el método nace al escribirse ---

    // El caso que motiva la feature: el laboratorio corre el examen por ELISA y
    // alguien lo escribe en la primera orden, sin haber configurado nada antes.
    @Test
    void firstUseOfAMethodCreatesItOnTheTemplate() {
        TestConfig config = newConfig("VIH");

        TestMethod created = testMethodService.resolveOrCreate(config, "ELISA");

        assertNotNull(created.getId());
        assertEquals("ELISA", created.getName());
        assertEquals(1, reread(config.getId()).getMethods().size(),
                "el método queda en el perfil para reutilizarlo");
    }

    // Y después se reutiliza el MISMO método, aunque se escriba con otras mayúsculas,
    // con espacios de sobra o sin tildes: si no, el perfil se llenaría de variantes de
    // la misma técnica y el selector las ofrecería todas.
    @Test
    void laterUsesReuseTheSameMethodRegardlessOfHowItIsTyped() {
        TestConfig config = newConfig("Toxoplasma");

        TestMethod first = testMethodService.resolveOrCreate(config, "Quimioluminiscencia");
        TestMethod second = testMethodService.resolveOrCreate(config, "  quimioluminiscencia ");

        assertEquals(first.getId(), second.getId(), "las dos escrituras son el mismo método");
        assertEquals(1, reread(config.getId()).getMethods().size());
        // El nombre visible es el de la primera vez.
        assertEquals("Quimioluminiscencia", second.getName());
    }

    @Test
    void aBlankNameCreatesNothing() {
        TestConfig config = newConfig("Hemograma");

        assertNull(testMethodService.resolveOrCreate(config, "   "));
        assertNull(testMethodService.resolveOrCreate(config, null));
        assertTrue(reread(config.getId()).getMethods().isEmpty());
    }

    // El límite del alcance: los métodos son del perfil. El mismo nombre bajo dos
    // exámenes son dos técnicas, y unirlas haría que renombrar en uno reescribiera
    // los reportes del otro.
    @Test
    void theSameNameOnTwoTemplatesGivesTwoIndependentMethods() {
        TestConfig vih = newConfig("VIH");
        TestConfig hepatitis = newConfig("Hepatitis B");

        TestMethod one = testMethodService.resolveOrCreate(vih, "ELISA");
        TestMethod other = testMethodService.resolveOrCreate(hepatitis, "ELISA");

        assertNotEquals(one.getId(), other.getId());
        assertEquals(1, reread(vih.getId()).getMethods().size());
        assertEquals(1, reread(hepatitis.getId()).getMethods().size());
    }

    // --- El predeterminado ---

    @Test
    void markingADefaultUnmarksThePreviousOne() {
        TestConfig config = newConfig("VIH");
        TestMethod elisa = testMethodService.resolveOrCreate(config, "ELISA");
        TestMethod quimio = testMethodService.resolveOrCreate(config, "Quimioluminiscencia");

        testMethodService.markAsDefault(elisa);
        testMethodService.markAsDefault(quimio);

        List<TestMethod> stored = reread(config.getId()).getMethods();
        assertEquals(2, stored.size(), "los dos siguen en el perfil");
        assertEquals(1, stored.stream().filter(TestMethod::isDefaultMethod).count(),
                "a lo sumo uno es el predeterminado");
        assertEquals("Quimioluminiscencia",
                stored.stream().filter(TestMethod::isDefaultMethod).findFirst().orElseThrow().getName());
    }

    // --- reconcile: el editor del examen ---

    @Test
    void savingTheTemplateWithThreeMethodsLeavesItHoldingThree() {
        TestConfig config = newConfig("Perfil Tiroideo");

        testMethodService.reconcile(config, List.of(
                row(null, "ELISA", false),
                row(null, "Quimioluminiscencia", true),
                row(null, "Aglutinación", false)));

        List<TestMethod> stored = reread(config.getId()).getMethods();
        assertEquals(3, stored.size());
        assertEquals("Quimioluminiscencia",
                stored.stream().filter(TestMethod::isDefaultMethod).findFirst().orElseThrow().getName());
    }

    // Quitar el predeterminado deja el perfil SIN predeterminado. No se asciende a
    // ningún otro: un perfil que se corre distinto cada vez es legítimo.
    @Test
    void removingTheDefaultLeavesTheTemplateWithNoneAndPromotesNothing() {
        TestConfig config = newConfig("VIH");
        testMethodService.reconcile(config, List.of(
                row(null, "ELISA", true),
                row(null, "Aglutinación", false)));
        config = reread(config.getId());
        TestMethod aglutinacion = config.getMethods().stream()
                .filter(m -> m.getName().equals("Aglutinación")).findFirst().orElseThrow();

        testMethodService.reconcile(config, List.of(row(aglutinacion.getId(), "Aglutinación", false)));

        List<TestMethod> stored = reread(config.getId()).getMethods();
        assertEquals(1, stored.size());
        assertFalse(stored.get(0).isDefaultMethod(), "no se asciende a nadie");
    }

    @Test
    void removingAnUnusedMethodSucceeds() {
        TestConfig config = newConfig("Hemograma");
        testMethodService.reconcile(config, List.of(row(null, "Citometría de flujo", false)));
        config = reread(config.getId());

        testMethodService.reconcile(config, List.of());

        assertTrue(reread(config.getId()).getMethods().isEmpty());
        assertTrue(testMethodRepository.findAll().isEmpty(), "la fila del método se borra de verdad");
    }

    // La diferencia deliberada con las etiquetas de orden: el método está impreso en
    // los reportes ya entregados, así que borrarlo no es corregir sino perder el dato
    // de con qué se produjo ese resultado. El rechazo dice cuántos exámenes lo tienen
    // para que se sepa de antemano el tamaño del trabajo de liberarlo.
    @Test
    void removingAMethodSomeExamUsesIsRefusedAndSaysHowMany() {
        TestConfig config = newConfig("VIH");
        TestMethod elisa = testMethodService.resolveOrCreate(config, "ELISA");
        newLabTestUsing(config, elisa);
        newLabTestUsing(config, elisa);
        TestConfig reloaded = reread(config.getId());

        APIException error = assertThrows(APIException.class,
                () -> testMethodService.reconcile(reloaded, List.of()));

        assertTrue(error.getMessage().contains("ELISA"), error.getMessage());
        assertTrue(error.getMessage().contains("2"), "dice cuántos exámenes lo indican: " + error.getMessage());
        assertEquals(1, reread(config.getId()).getMethods().size(), "el método sigue en el perfil");
    }

    // Renombrar SÍ se propaga: un nombre mal escrito ya está mal en los reportes
    // emitidos, y propagar la corrección es justo el punto de hacerla. No cambia QUÉ
    // técnica nombra el reporte, solo cómo se escribe.
    @Test
    void renamingAMethodReachesTheExamsThatUsedIt() {
        TestConfig config = newConfig("Toxoplasma");
        TestMethod typo = testMethodService.resolveOrCreate(config, "quimioluminicencia");
        LabTest labTest = newLabTestUsing(config, typo);
        TestConfig reloaded = reread(config.getId());
        Long methodId = reloaded.getMethods().get(0).getId();

        testMethodService.reconcile(reloaded, List.of(row(methodId, "Quimioluminiscencia", false)));

        entityManager.flush();
        entityManager.clear();
        assertEquals("Quimioluminiscencia",
                labTestRepository.findById(labTest.getId()).orElseThrow().getMethod().getName());
        assertEquals(1, reread(config.getId()).getMethods().size(), "es el mismo método, corregido");
    }

    // Renombrar uno al nombre de otro que el perfil conserva fusionaría dos técnicas
    // en los reportes que las nombran: se rechaza, como en etiquetas y médicos.
    @Test
    void renamingOntoANameTheSameTemplateHoldsIsRefused() {
        TestConfig config = newConfig("VIH");
        testMethodService.reconcile(config, List.of(
                row(null, "ELISA", false),
                row(null, "Aglutinación", false)));
        TestConfig reloaded = reread(config.getId());
        List<TestMethod> stored = new ArrayList<>(reloaded.getMethods());
        TestMethod aglutinacion = stored.stream()
                .filter(m -> m.getName().equals("Aglutinación")).findFirst().orElseThrow();
        TestMethod elisa = stored.stream()
                .filter(m -> m.getName().equals("ELISA")).findFirst().orElseThrow();

        assertThrows(APIException.class, () -> testMethodService.reconcile(reloaded, List.of(
                row(elisa.getId(), "ELISA", false),
                row(aglutinacion.getId(), "elisa", false))));

        entityManager.clear();
        assertEquals(2, reread(config.getId()).getMethods().size(), "el perfil queda como estaba");
    }
}
