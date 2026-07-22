package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.InvoiceDTO;
import marroquinsoftware.labflowapi.payload.InvoiceRequest;
import marroquinsoftware.labflowapi.payload.PaymentRequest;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Recorre los eventos de facturación (contado, crédito, abonos, anulaciones)
 * verificando el documento y su rastro contable: cada evento deja una partida
 * que cuadra y las cuentas correctas.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({InvoiceServiceImp.class, JournalServiceImp.class, AccountSeeder.class, CaiNumberService.class,
        AgeDiscountCalculator.class, AmountInWordsConverter.class, TenantIdentifierResolver.class,
        InvoiceAccountingTest.JacksonForTest.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class InvoiceAccountingTest {

    static class JacksonForTest {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
    }

    @Autowired InvoiceService invoiceService;
    @Autowired JournalService journalService;
    @Autowired AccountSeeder accountSeeder;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired TestRepository testRepository;
    @Autowired LabOrderRepository labOrderRepository;
    @Autowired JournalEntryRepository journalEntryRepository;

    private Laboratory laboratory;
    private Customer customer;

    @BeforeEach
    void setUp() {
        laboratory = new Laboratory();
        laboratory.setName("Laboratorio de Prueba");
        laboratory.setRtn("08011999123456");
        laboratory.setCai1("254F86-612421-9701AB-016921-3E7CD1-35");
        laboratory.setCai1ExpirationDate(LocalDate.now().plusMonths(6));
        laboratory.setCai1RangeFrom("000-001-01-00000001");
        laboratory.setCai1RangeTo("000-001-01-00000100");
        // Tercera edad desde los 60 años con 10% (para el test de descuento).
        laboratory.setThirdAgeMinYears(60);
        laboratory.setThirdAgeDiscountPercent(new BigDecimal("10.00"));
        laboratory = laboratoryRepository.save(laboratory);

        // El id del laboratorio es el tenant que leen los servicios en runtime.
        TenantContext.setLaboratoryId(laboratory.getId());
        accountSeeder.seedDefaultAccounts();

        customer = new Customer();
        customer.setName("Paciente de Prueba");
        customer.setAgeInDays(30 * 365); // 30 años: sin descuento
        customer = customerRepository.save(customer);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    private LabOrder newOrder(Customer forCustomer, String... testNamesAndPrices) {
        LabOrder order = new LabOrder();
        order.setCustomer(forCustomer);
        order.setStatus(OrderStatus.PENDING);
        List<LabTest> labTests = new ArrayList<>();
        for (int i = 0; i < testNamesAndPrices.length; i += 2) {
            marroquinsoftware.labflowapi.model.Test test = new marroquinsoftware.labflowapi.model.Test();
            test.setName(testNamesAndPrices[i]);
            test.setPrice(new BigDecimal(testNamesAndPrices[i + 1]));
            test = testRepository.save(test);
            LabTest labTest = new LabTest();
            labTest.setOrder(order);
            labTest.setTest(test);
            labTests.add(labTest);
        }
        order.setTests(labTests);
        return labOrderRepository.save(order);
    }

    private InvoiceRequest contado(Long orderId, String amount) {
        return new InvoiceRequest(orderId, SaleCondition.CONTADO, null,
                new PaymentRequest(new BigDecimal(amount), PaymentMethod.EFECTIVO, null));
    }

    private InvoiceRequest credito(Long orderId) {
        return new InvoiceRequest(orderId, SaleCondition.CREDITO, null, null);
    }

    private JournalEntry entryOf(JournalSourceType type, Long sourceId) {
        return journalEntryRepository.findFirstBySourceTypeAndSourceId(type, sourceId)
                .orElseThrow(() -> new AssertionError("falta la partida " + type + " del documento " + sourceId));
    }

    private void assertBalanced(JournalEntry entry) {
        BigDecimal debits = entry.getLines().stream().map(JournalLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = entry.getLines().stream().map(JournalLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, debits.compareTo(credits), "la partida " + entry.getEntryNumber() + " no cuadra");
    }

    private BigDecimal lineAmount(JournalEntry entry, SystemAccountKey key, boolean debit) {
        return entry.getLines().stream()
                .filter(l -> l.getAccount().getSystemKey() == key)
                .map(l -> debit ? l.getDebit() : l.getCredit())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void contadoInvoiceIsPaidAndBooksIssueAndPayment() {
        LabOrder order = newOrder(customer, "Hemograma", "300.00", "Glucosa", "200.00");

        InvoiceDTO dto = invoiceService.createInvoice(contado(order.getId(), "500.00"));

        assertEquals("000-001-01-00000001", dto.getInvoiceNumber());
        assertEquals(InvoiceStatus.PAGADA, dto.getStatus());
        assertEquals(0, dto.getBalance().compareTo(BigDecimal.ZERO));
        assertEquals("QUINIENTOS LEMPIRAS CON 00/100", dto.getTotalInWords());
        assertEquals(2, dto.getItems().size());
        assertEquals(1, dto.getPayments().size());

        // Emisión: CxC 500 contra Ingresos 500.
        JournalEntry issue = entryOf(JournalSourceType.FACTURA, dto.getId());
        assertBalanced(issue);
        assertEquals(0, lineAmount(issue, SystemAccountKey.CUENTAS_POR_COBRAR, true)
                .compareTo(new BigDecimal("500.00")));
        assertEquals(0, lineAmount(issue, SystemAccountKey.INGRESOS_SERVICIOS, false)
                .compareTo(new BigDecimal("500.00")));

        // Pago: Caja 500 contra CxC 500 (partida propia, aunque sea contado).
        JournalEntry payment = entryOf(JournalSourceType.PAGO, dto.getPayments().get(0).getId());
        assertBalanced(payment);
        assertEquals(0, lineAmount(payment, SystemAccountKey.CAJA, true)
                .compareTo(new BigDecimal("500.00")));
        assertEquals(0, lineAmount(payment, SystemAccountKey.CUENTAS_POR_COBRAR, false)
                .compareTo(new BigDecimal("500.00")));
    }

    @Test
    void contadoRequiresTheFullTotal() {
        LabOrder order = newOrder(customer, "Hemograma", "500.00");
        APIException ex = assertThrows(APIException.class,
                () -> invoiceService.createInvoice(contado(order.getId(), "400.00")));
        assertTrue(ex.getMessage().contains("pago completo"), ex.getMessage());
    }

    @Test
    void creditoFlowsFromPendingThroughPartialToPaid() {
        LabOrder order = newOrder(customer, "Perfil lipídico", "500.00");
        InvoiceDTO dto = invoiceService.createInvoice(credito(order.getId()));
        assertEquals(InvoiceStatus.PENDIENTE, dto.getStatus());

        dto = invoiceService.registerPayment(dto.getId(),
                new PaymentRequest(new BigDecimal("200.00"), PaymentMethod.EFECTIVO, null));
        assertEquals(InvoiceStatus.PARCIAL, dto.getStatus());
        assertEquals(0, dto.getBalance().compareTo(new BigDecimal("300.00")));

        // Un abono mayor al saldo se rechaza.
        Long invoiceId = dto.getId();
        APIException overpay = assertThrows(APIException.class, () -> invoiceService.registerPayment(invoiceId,
                new PaymentRequest(new BigDecimal("400.00"), PaymentMethod.TARJETA, "V-123")));
        assertTrue(overpay.getMessage().contains("excede el saldo"), overpay.getMessage());

        dto = invoiceService.registerPayment(invoiceId,
                new PaymentRequest(new BigDecimal("300.00"), PaymentMethod.TRANSFERENCIA, "T-456"));
        assertEquals(InvoiceStatus.PAGADA, dto.getStatus());

        // El pago con transferencia entra a Bancos, no a Caja.
        JournalEntry bankPayment = entryOf(JournalSourceType.PAGO, dto.getPayments().get(1).getId());
        assertEquals(0, lineAmount(bankPayment, SystemAccountKey.BANCOS, true)
                .compareTo(new BigDecimal("300.00")));

        assertThrows(APIException.class, () -> invoiceService.registerPayment(invoiceId,
                new PaymentRequest(new BigDecimal("1.00"), PaymentMethod.EFECTIVO, null)));
    }

    @Test
    void ageDiscountIsAppliedAndBookedAgainstDiscountsAccount() {
        Customer senior = new Customer();
        senior.setName("Paciente Tercera Edad");
        senior.setAgeInDays(70 * 365);
        senior = customerRepository.save(senior);
        LabOrder order = newOrder(senior, "Hemograma", "300.00", "Glucosa", "200.00");

        InvoiceDTO dto = invoiceService.createInvoice(credito(order.getId()));

        assertEquals(AgeDiscountKind.THIRD_AGE, dto.getDiscountKind());
        assertEquals(0, dto.getDiscountAmount().compareTo(new BigDecimal("50.00")));
        assertEquals(0, dto.getTotal().compareTo(new BigDecimal("450.00")));

        // Emisión: CxC 450 + Descuentos 50 contra Ingresos 500.
        JournalEntry issue = entryOf(JournalSourceType.FACTURA, dto.getId());
        assertBalanced(issue);
        assertEquals(0, lineAmount(issue, SystemAccountKey.CUENTAS_POR_COBRAR, true)
                .compareTo(new BigDecimal("450.00")));
        assertEquals(0, lineAmount(issue, SystemAccountKey.DESCUENTOS_VENTAS, true)
                .compareTo(new BigDecimal("50.00")));
        assertEquals(0, lineAmount(issue, SystemAccountKey.INGRESOS_SERVICIOS, false)
                .compareTo(new BigDecimal("500.00")));
    }

    @Test
    void ordersCannotBeInvoicedTwiceUntilAnnulled() {
        LabOrder order = newOrder(customer, "Hemograma", "100.00");
        InvoiceDTO first = invoiceService.createInvoice(credito(order.getId()));

        APIException ex = assertThrows(APIException.class,
                () -> invoiceService.createInvoice(credito(order.getId())));
        assertTrue(ex.getMessage().contains(first.getInvoiceNumber()), ex.getMessage());

        invoiceService.annulInvoice(first.getId(), "Error de emisión");

        InvoiceDTO second = invoiceService.createInvoice(credito(order.getId()));
        assertEquals("000-001-01-00000002", second.getInvoiceNumber(),
                "tras anular se refactura con el siguiente número");
    }

    @Test
    void annullingAnInvoiceWithPaymentsReversesEverything() {
        LabOrder order = newOrder(customer, "Hemograma", "500.00");
        InvoiceDTO dto = invoiceService.createInvoice(credito(order.getId()));
        dto = invoiceService.registerPayment(dto.getId(),
                new PaymentRequest(new BigDecimal("200.00"), PaymentMethod.EFECTIVO, null));
        Long paymentId = dto.getPayments().get(0).getId();

        dto = invoiceService.annulInvoice(dto.getId(), "El paciente devolvió la orden");

        assertEquals(InvoiceStatus.ANULADA, dto.getStatus());
        assertEquals(0, dto.getBalance().compareTo(BigDecimal.ZERO));
        assertTrue(dto.getPayments().stream().allMatch(p -> p.isAnnulled()),
                "anular la factura anula sus pagos");

        JournalEntry paymentReversal = entryOf(JournalSourceType.ANULACION_PAGO, paymentId);
        assertBalanced(paymentReversal);
        assertEquals(0, lineAmount(paymentReversal, SystemAccountKey.CUENTAS_POR_COBRAR, true)
                .compareTo(new BigDecimal("200.00")));

        JournalEntry issueReversal = entryOf(JournalSourceType.ANULACION_FACTURA, dto.getId());
        assertBalanced(issueReversal);
        assertEquals(0, lineAmount(issueReversal, SystemAccountKey.CUENTAS_POR_COBRAR, false)
                .compareTo(new BigDecimal("500.00")));

        Long invoiceId = dto.getId();
        assertThrows(APIException.class,
                () -> invoiceService.annulInvoice(invoiceId, "otra vez"),
                "no se anula dos veces");
    }

    @Test
    void annullingAPaymentRestoresTheBalance() {
        LabOrder order = newOrder(customer, "Hemograma", "500.00");
        InvoiceDTO dto = invoiceService.createInvoice(credito(order.getId()));
        dto = invoiceService.registerPayment(dto.getId(),
                new PaymentRequest(new BigDecimal("200.00"), PaymentMethod.EFECTIVO, null));
        Long paymentId = dto.getPayments().get(0).getId();

        dto = invoiceService.annulPayment(dto.getId(), paymentId, "Se cobró por error");

        assertEquals(InvoiceStatus.PENDIENTE, dto.getStatus());
        assertEquals(0, dto.getPaidAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, dto.getBalance().compareTo(new BigDecimal("500.00")));
        assertBalanced(entryOf(JournalSourceType.ANULACION_PAGO, paymentId));
    }

    @Test
    void cancelledOrEmptyOrdersCannotBeInvoiced() {
        LabOrder cancelled = newOrder(customer, "Hemograma", "100.00");
        cancelled.setStatus(OrderStatus.CANCELLED);
        labOrderRepository.save(cancelled);
        assertThrows(APIException.class, () -> invoiceService.createInvoice(credito(cancelled.getId())));

        LabOrder empty = new LabOrder();
        empty.setCustomer(customer);
        empty.setStatus(OrderStatus.PENDING);
        LabOrder savedEmpty = labOrderRepository.save(empty);
        assertThrows(APIException.class, () -> invoiceService.createInvoice(credito(savedEmpty.getId())));
    }
}
