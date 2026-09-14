package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.InvoiceDTO;
import marroquinsoftware.labflowapi.payload.InvoiceRequest;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.LabTestDTO;
import marroquinsoftware.labflowapi.payload.TestResultDTO;
import marroquinsoftware.labflowapi.payload.TestRunDTO;
import marroquinsoftware.labflowapi.repositories.*;
import marroquinsoftware.labflowapi.service.*;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import marroquinsoftware.labflowapi.tenant.TenantIdentifierResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El candado de composición: una orden con factura viva no acepta que se le
 * agreguen ni se le quiten exámenes, y todo lo demás sobre esa orden sigue
 * funcionando igual.
 *
 * <p>La factura es un documento fiscal (CAI): congela el nombre y el precio de
 * cada examen que cobra. Sin este candado, la orden podía ganar un examen que
 * nadie cobró o perder uno ya pagado, y nada lo detectaba después.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({LabTestServiceImp.class, LabOrderServiceImp.class, OrderTagServiceImp.class,
        TestRunServiceImp.class, InvoiceServiceImp.class, JournalServiceImp.class,
        AccountSeeder.class, CaiNumberService.class, AgeDiscountCalculator.class,
        InvoiceTotalsCalculator.class, AmountInWordsConverter.class, ReferralServiceImp.class,
        TenantIdentifierResolver.class, OrderTestLockTest.TestBeans.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class OrderTestLockTest {

    /**
     * Lo mínimo para levantar los servicios: el ModelMapper que usa LabTestServiceImp
     * y un almacenamiento de archivos que no existe (acá no se sube nada; el R2 real
     * necesita credenciales y ninguna prueba de este archivo adjunta imágenes).
     */
    static class TestBeans {
        @Bean org.modelmapper.ModelMapper modelMapper() { return new org.modelmapper.ModelMapper(); }
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
        @Bean FileStorageService fileStorageService() {
            return new FileStorageService() {
                @Override public boolean isEnabled() { return false; }
                @Override public String upload(String key, byte[] content, String contentType) {
                    throw new UnsupportedOperationException();
                }
                @Override public String signedUrl(String key, Duration ttl) { return null; }
                @Override public void delete(String key) { }
            };
        }
    }

    @Autowired LabTestService labTestService;
    @Autowired LabOrderService labOrderService;
    @Autowired TestRunService testRunService;
    @Autowired InvoiceService invoiceService;
    @Autowired AccountSeeder accountSeeder;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired TestRepository testRepository;
    @Autowired TestConfigRepository testConfigRepository;
    @Autowired ParameterRepository parameterRepository;
    @Autowired LabOrderRepository labOrderRepository;
    @Autowired LabTestRepository labTestRepository;

    private Customer customer;
    private marroquinsoftware.labflowapi.model.Test hemograma;
    private marroquinsoftware.labflowapi.model.Test glucosa;

    @BeforeEach
    void setUp() {
        Laboratory laboratory = new Laboratory();
        laboratory.setName("Laboratorio de Prueba");
        laboratory.setRtn("08011999123456");
        laboratory.setCai1("254F86-612421-9701AB-016921-3E7CD1-35");
        laboratory.setCai1ExpirationDate(LocalDate.now().plusMonths(6));
        laboratory.setCai1RangeFrom("000-001-01-00000001");
        laboratory.setCai1RangeTo("000-001-01-00000100");
        laboratory = laboratoryRepository.save(laboratory);

        TenantContext.setLaboratoryId(laboratory.getId());
        accountSeeder.seedDefaultAccounts();

        customer = new Customer();
        customer.setName("Paciente de Prueba");
        customer.setSex(Sex.FEMALE);
        customer.setAgeInDays(30 * 365);
        customer.setNationalIdNumber("0801-1990-01234");
        customer = customerRepository.save(customer);

        hemograma = newTest("Hemograma", "500.00");
        glucosa = newTest("Glucosa", "150.00");
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    // --- Utilidades ---

    private marroquinsoftware.labflowapi.model.Test newTest(String name, String price) {
        marroquinsoftware.labflowapi.model.Test test = new marroquinsoftware.labflowapi.model.Test();
        test.setName(name);
        test.setPrice(new BigDecimal(price));
        return testRepository.save(test);
    }

    /** Una orden con los exámenes indicados, creada directo por el repositorio. */
    private LabOrder newOrder(marroquinsoftware.labflowapi.model.Test... tests) {
        LabOrder order = new LabOrder();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        List<LabTest> labTests = new ArrayList<>();
        for (marroquinsoftware.labflowapi.model.Test test : tests) {
            LabTest labTest = new LabTest();
            labTest.setOrder(order);
            labTest.setTest(test);
            labTests.add(labTest);
        }
        order.setTests(labTests);
        return labOrderRepository.save(order);
    }

    private InvoiceDTO invoice(Long orderId) {
        return invoiceService.createInvoice(
                new InvoiceRequest(orderId, SaleCondition.CREDITO, null, null, null, null, null));
    }

    private List<LabTest> testsOf(Long orderId) {
        return labTestRepository.findByOrder_Id(orderId);
    }

    private LabTestDTO requesting(marroquinsoftware.labflowapi.model.Test test) {
        LabTestDTO dto = new LabTestDTO();
        dto.setTestId(test.getId());
        return dto;
    }

    // --- Datos del paciente embebidos en el DTO de la orden (sin 2.ª llamada) ---

    @Test
    void orderDtoEmbedsPatientIdentityWithoutASecondCall() {
        LabOrder order = newOrder(hemograma);

        LabOrderDTO dto = labOrderService.getOrderById(order.getId());

        // El detalle/impresión/sobre leen estos campos del DTO en vez de pedir
        // GET /customers/{id}: nombre, sexo, edad e identidad del paciente.
        assertEquals("Paciente de Prueba", dto.getCustomerName());
        assertEquals(Sex.FEMALE, dto.getCustomerSex());
        assertEquals(Integer.valueOf(30 * 365), dto.getCustomerAgeInDays());
        assertEquals("0801-1990-01234", dto.getCustomerNationalId());
    }

    // --- 3.2 El candado ---

    @Test
    void addingAnExamToAnInvoicedOrderIsRefused() {
        LabOrder order = newOrder(hemograma);
        InvoiceDTO factura = invoice(order.getId());
        List<Long> before = testsOf(order.getId()).stream().map(LabTest::getId).toList();

        APIException error = assertThrows(APIException.class,
                () -> labTestService.addTestToOrder(order.getId(), requesting(glucosa)));

        // El mensaje nombra la factura: sin el número, quien atiende no sabe cuál anular.
        assertTrue(error.getMessage().contains(factura.getInvoiceNumber()),
                "El rechazo debe nombrar la factura, decía: " + error.getMessage());
        // Y no cambió nada.
        assertEquals(before, testsOf(order.getId()).stream().map(LabTest::getId).toList());
    }

    @Test
    void removingAnExamFromAnInvoicedOrderIsRefused() {
        LabOrder order = newOrder(hemograma, glucosa);
        InvoiceDTO factura = invoice(order.getId());
        List<LabTest> before = testsOf(order.getId());
        Long victim = before.get(0).getId();

        APIException error = assertThrows(APIException.class,
                () -> labTestService.removeTestFromOrder(order.getId(), victim));

        assertTrue(error.getMessage().contains(factura.getInvoiceNumber()),
                "El rechazo debe nombrar la factura, decía: " + error.getMessage());
        assertEquals(before.stream().map(LabTest::getId).toList(),
                testsOf(order.getId()).stream().map(LabTest::getId).toList());
    }

    // --- 3.3 Sin factura, nada cambia ---

    @Test
    void anOrderWithNoInvoiceIsUnaffected() {
        LabOrder order = newOrder(hemograma);

        LabTestDTO added = labTestService.addTestToOrder(order.getId(), requesting(glucosa));
        assertEquals(2, testsOf(order.getId()).size());

        labTestService.removeTestFromOrder(order.getId(), added.getId());
        assertEquals(1, testsOf(order.getId()).size());
    }

    // --- 3.4 Anular libera el candado ---

    @Test
    void annullingTheInvoiceReleasesTheLock() {
        LabOrder order = newOrder(hemograma);
        InvoiceDTO factura = invoice(order.getId());
        assertThrows(APIException.class,
                () -> labTestService.addTestToOrder(order.getId(), requesting(glucosa)));

        invoiceService.annulInvoice(factura.getId(), "Se facturaron exámenes equivocados");

        // Anular → corregir → refacturar es la única vía de corrección, y funciona.
        labTestService.addTestToOrder(order.getId(), requesting(glucosa));
        assertEquals(2, testsOf(order.getId()).size());
    }

    // --- 3.5 Una factura nueva vuelve a bloquear ---

    @Test
    void aSecondInvoiceLocksTheOrderAgain() {
        LabOrder order = newOrder(hemograma);
        InvoiceDTO primera = invoice(order.getId());
        invoiceService.annulInvoice(primera.getId(), "Exámenes equivocados");
        labTestService.addTestToOrder(order.getId(), requesting(glucosa));

        InvoiceDTO segunda = invoice(order.getId());

        APIException error = assertThrows(APIException.class,
                () -> labTestService.addTestToOrder(order.getId(), requesting(newTest("Urea", "200.00"))));
        assertTrue(error.getMessage().contains(segunda.getInvoiceNumber()),
                "Debe nombrar la factura nueva, decía: " + error.getMessage());
        assertEquals(2, testsOf(order.getId()).size());
    }

    // --- 3.6 Todo lo demás sigue disponible ---

    @Test
    void everythingElseAboutAnInvoicedOrderStaysAvailable() {
        LabOrder order = newOrder(hemograma);
        invoice(order.getId());
        Long labTestId = testsOf(order.getId()).get(0).getId();

        // Perfil.
        TestConfig config = new TestConfig();
        config.setTest(hemograma);
        config.setName("Hemograma completo");
        config.setActive(true);
        config = testConfigRepository.save(config);
        assertEquals(config.getId(),
                labTestService.assignTestConfig(order.getId(), labTestId, config.getId()).getTestConfigId());

        // Notas, tipo de muestra y método.
        assertEquals("Ayuno de 8 horas",
                labTestService.updateNotes(order.getId(), labTestId, "Ayuno de 8 horas").getNotes());
        assertEquals("Sangre venosa",
                labTestService.updateSampleType(order.getId(), labTestId, "Sangre venosa").getSampleType());
        assertEquals("Citometría de flujo",
                labTestService.updateMethod(order.getId(), labTestId, "Citometría de flujo").getMethod());

        // Registrar un resultado: esto es lo que el candado NO debe tocar.
        Parameter hemoglobina = new Parameter();
        hemoglobina.setName("Hemoglobina");
        hemoglobina.setValueType(ParameterValueType.QUANTITATIVE);
        hemoglobina = parameterRepository.save(hemoglobina);

        TestResultDTO result = new TestResultDTO();
        result.setParameterId(hemoglobina.getId());
        result.setValue("13.5");
        TestRunDTO run = new TestRunDTO();
        run.setResults(List.of(result));

        TestRunDTO saved = testRunService.addRunToTest(labTestId, run);
        assertNotNull(saved.getId());
        assertEquals("13.5", saved.getResults().get(0).getValue());
    }

    // --- 2.5 Crear la orden con sus exámenes no lleva candado ---

    @Test
    void creatingAnOrderWithItsExamsIsUnaffected() {
        LabOrderDTO dto = new LabOrderDTO();
        dto.setCustomerId(customer.getId());
        dto.setTestIds(List.of(hemograma.getId(), glucosa.getId()));

        LabOrderDTO created = labOrderService.createOrder(dto);

        // Una orden no puede estar facturada antes de existir.
        assertEquals(2, testsOf(created.getId()).size());
        assertFalse(created.isTestsLocked());
    }

    // --- 3.7 El modelo de lectura ---

    @Test
    void anInvoicedOrderReportsItsExamsAsLocked() {
        LabOrder order = newOrder(hemograma);
        invoice(order.getId());

        assertTrue(labOrderService.getOrderById(order.getId()).isTestsLocked());
        // También en el listado, que comparte toDTO.
        assertTrue(labOrderService.getAllOrders(0, 50, "id", "desc", null, null)
                .getContent().stream()
                .filter(o -> o.getId().equals(order.getId()))
                .findFirst().orElseThrow()
                .isTestsLocked());
    }

    @Test
    void anOrderWithNoInvoiceOrOnlyAnAnnulledOneReportsItsExamsAsUnlocked() {
        LabOrder nunca = newOrder(hemograma);
        assertFalse(labOrderService.getOrderById(nunca.getId()).isTestsLocked());

        LabOrder anulada = newOrder(glucosa);
        InvoiceDTO factura = invoice(anulada.getId());
        assertTrue(labOrderService.getOrderById(anulada.getId()).isTestsLocked());

        invoiceService.annulInvoice(factura.getId(), "Anulada");
        assertFalse(labOrderService.getOrderById(anulada.getId()).isTestsLocked());
    }

    @Test
    void aClientCannotSetTheLockBySendingTheField() {
        LabOrderDTO dto = new LabOrderDTO();
        dto.setCustomerId(customer.getId());
        dto.setTestsLocked(true);

        LabOrderDTO created = labOrderService.createOrder(dto);
        assertFalse(created.isTestsLocked());
        assertFalse(labOrderService.getOrderById(created.getId()).isTestsLocked());

        LabOrderDTO update = new LabOrderDTO();
        update.setCustomerId(customer.getId());
        update.setTestsLocked(true);
        assertFalse(labOrderService.updateOrder(update, created.getId()).isTestsLocked());
        assertFalse(labOrderService.getOrderById(created.getId()).isTestsLocked());
    }
}
