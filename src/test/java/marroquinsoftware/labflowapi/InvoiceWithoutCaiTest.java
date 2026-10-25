package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.CollectionsReportDTO;
import marroquinsoftware.labflowapi.payload.InvoiceDTO;
import marroquinsoftware.labflowapi.payload.InvoiceRequest;
import marroquinsoftware.labflowapi.payload.PaymentRequest;
import marroquinsoftware.labflowapi.payload.ReceivablesResponse;
import marroquinsoftware.labflowapi.payload.SalesRegisterDTO;
import marroquinsoftware.labflowapi.payload.SalesReportDTO;
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
 * Facturar sin CAI: con el interruptor del laboratorio encendido toda factura sale
 * como documento no fiscal — correlativo propio, sin CAI, sin contabilidad — aunque
 * haya un CAI vigente; apagado, todo sigue como antes. El tipo queda congelado en
 * cada documento, y cuentas por cobrar y reportes lo distinguen.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({InvoiceServiceImp.class, JournalServiceImp.class, AccountSeeder.class, CaiNumberService.class,
        AgeDiscountCalculator.class, InvoiceTotalsCalculator.class, AmountInWordsConverter.class,
        AccountingPeriodServiceImp.class, AnalyticsReportServiceImp.class, TenantIdentifierResolver.class,
        InvoiceWithoutCaiTest.JacksonForTest.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class InvoiceWithoutCaiTest {

    static class JacksonForTest {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
    }

    @Autowired InvoiceService invoiceService;
    @Autowired AnalyticsReportService analyticsReportService;
    @Autowired AccountSeeder accountSeeder;
    @Autowired AccountingPeriodService accountingPeriodService;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired TestRepository testRepository;
    @Autowired LabOrderRepository labOrderRepository;
    @Autowired JournalEntryRepository journalEntryRepository;
    @PersistenceContext EntityManager entityManager;

    private Long laboratoryId;
    private Customer customer;

    @BeforeEach
    void setUp() {
        Laboratory laboratory = new Laboratory();
        laboratory.setName("Laboratorio de Prueba");
        laboratory.setRtn("08011999123456");
        laboratory.setAddress1("Col. Palmira");
        laboratory.setInvoiceFooterNote("Gracias por su visita");
        laboratory.setCai1("254F86-612421-9701AB-016921-3E7CD1-35");
        laboratory.setCai1ExpirationDate(LocalDate.now().plusMonths(6));
        laboratory.setCai1RangeFrom("000-001-01-00000001");
        laboratory.setCai1RangeTo("000-001-01-00000100");
        laboratoryId = laboratoryRepository.save(laboratory).getId();

        TenantContext.setLaboratoryId(laboratoryId);
        accountSeeder.seedDefaultAccounts();

        customer = new Customer();
        customer.setName("Paciente de Prueba");
        customer.setAgeInDays(30 * 365);
        customer = customerRepository.save(customer);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    private Laboratory lab() {
        return laboratoryRepository.findById(laboratoryId).orElseThrow();
    }

    private void setWithoutCai(boolean on) {
        Laboratory laboratory = lab();
        laboratory.setInvoiceWithoutCai(on);
        laboratoryRepository.save(laboratory);
    }

    private LabOrder newOrder(String testName, String price) {
        LabOrder order = new LabOrder();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        marroquinsoftware.labflowapi.model.Test test = new marroquinsoftware.labflowapi.model.Test();
        test.setName(testName);
        test.setPrice(new BigDecimal(price));
        test = testRepository.save(test);
        LabTest labTest = new LabTest();
        labTest.setOrder(order);
        labTest.setTest(test);
        List<LabTest> tests = new ArrayList<>();
        tests.add(labTest);
        order.setTests(tests);
        return labOrderRepository.save(order);
    }

    private InvoiceRequest contado(Long orderId, String amount) {
        return new InvoiceRequest(orderId, SaleCondition.CONTADO, null, null, null, null, null,
                new PaymentRequest(new BigDecimal(amount), PaymentMethod.EFECTIVO, null));
    }

    private InvoiceRequest credito(Long orderId) {
        return new InvoiceRequest(orderId, SaleCondition.CREDITO, null, null, null, null, null, null);
    }

    private InvoiceDTO issueCredit(String price) {
        return invoiceService.createInvoice(credito(newOrder("Examen " + price, price).getId()));
    }

    // --- Tipo de documento --------------------------------------------------------

    @Test
    void switchOffIssuesAFiscalInvoiceFromTheCai() {
        InvoiceDTO dto = issueCredit("500.00");

        assertTrue(dto.isFiscal());
        assertEquals("000-001-01-00000001", dto.getInvoiceNumber());
        assertEquals("254F86-612421-9701AB-016921-3E7CD1-35", dto.getCai());
        assertEquals("000-001-01-00000002", lab().getCai1CurrentNumber());
    }

    @Test
    void switchOffWithoutAUsableCaiStillBlocksIssuing() {
        Laboratory laboratory = lab();
        laboratory.setCai1ExpirationDate(LocalDate.now().minusDays(1));
        laboratoryRepository.save(laboratory);
        LabOrder order = newOrder("Hemograma", "500.00");
        long entriesBefore = journalEntryRepository.count();

        APIException error = assertThrows(APIException.class,
                () -> invoiceService.createInvoice(credito(order.getId())));

        assertTrue(error.getMessage().contains("No hay un CAI vigente"), error.getMessage());
        assertEquals(entriesBefore, journalEntryRepository.count());
    }

    @Test
    void switchOnIgnoresAValidCai() {
        setWithoutCai(true);

        InvoiceDTO dto = issueCredit("500.00");

        assertFalse(dto.isFiscal());
        assertEquals("00000001", dto.getInvoiceNumber());
        assertNull(lab().getCai1CurrentNumber(), "no se gastó ningún número del CAI");
    }

    @Test
    void switchOnWorksWithoutAnyCaiConfigured() {
        Laboratory laboratory = lab();
        laboratory.setCai1(null);
        laboratory.setCai1ExpirationDate(null);
        laboratory.setCai1RangeFrom(null);
        laboratory.setCai1RangeTo(null);
        laboratory.setInvoiceWithoutCai(true);
        laboratoryRepository.save(laboratory);

        InvoiceDTO dto = issueCredit("500.00");

        assertFalse(dto.isFiscal());
        assertEquals("00000001", dto.getInvoiceNumber());
    }

    @Test
    void otherValidationsStillApplyAndDoNotBurnANumber() {
        setWithoutCai(true);
        LabOrder order = newOrder("Hemograma", "500.00");
        invoiceService.createInvoice(credito(order.getId()));

        // La misma orden otra vez y una fecha futura: se rechazan antes de numerar.
        // (El contado sin pago completo se rechaza después de guardar y lo deshace
        // el rollback de la transacción, igual que con el correlativo del CAI; acá
        // no se ve porque @DataJpaTest corre todo el test en una sola transacción.)
        assertThrows(APIException.class, () -> invoiceService.createInvoice(credito(order.getId())));
        LabOrder other = newOrder("Glucosa", "100.00");
        InvoiceRequest future = credito(other.getId());
        future.setIssueDate(LocalDate.now().plusDays(1));
        assertThrows(APIException.class, () -> invoiceService.createInvoice(future));

        assertEquals("00000002", invoiceService.createInvoice(credito(other.getId())).getInvoiceNumber(),
                "los intentos rechazados no consumieron correlativo");
    }

    // --- Correlativo ----------------------------------------------------------------

    @Test
    void theInternalCorrelativeSurvivesTogglingTheSwitch() {
        setWithoutCai(true);
        assertEquals("00000001", issueCredit("100.00").getInvoiceNumber());
        assertEquals("00000002", issueCredit("110.00").getInvoiceNumber());

        setWithoutCai(false);
        assertEquals("000-001-01-00000001", issueCredit("120.00").getInvoiceNumber());

        setWithoutCai(true);
        assertEquals("00000003", issueCredit("130.00").getInvoiceNumber());
        assertEquals("000-001-01-00000002", lab().getCai1CurrentNumber(),
                "el CAI solo avanzó por la factura fiscal");
    }

    // --- Lo que se congela ----------------------------------------------------------

    @Test
    void aNonFiscalDocumentHasNoCaiDataButKeepsTheHeader() {
        setWithoutCai(true);

        InvoiceDTO dto = issueCredit("500.00");

        assertNull(dto.getCai());
        assertNull(dto.getCaiRangeFrom());
        assertNull(dto.getCaiRangeTo());
        assertNull(dto.getCaiExpirationDate());
        assertEquals("Laboratorio de Prueba", dto.getLabName());
        assertEquals("08011999123456", dto.getLabRtn());
        assertEquals("Gracias por su visita", dto.getLabFooterNote());
        assertEquals("Paciente de Prueba", dto.getCustomerName());
    }

    @Test
    void togglingTheSwitchDoesNotRewriteIssuedDocuments() {
        setWithoutCai(true);
        InvoiceDTO nonFiscal = issueCredit("500.00");
        setWithoutCai(false);
        InvoiceDTO fiscal = issueCredit("200.00");
        entityManager.flush();
        entityManager.clear();

        InvoiceDTO reread = invoiceService.getInvoice(nonFiscal.getId());
        assertFalse(reread.isFiscal());
        assertNull(reread.getCai());

        setWithoutCai(true);
        InvoiceDTO rereadFiscal = invoiceService.getInvoice(fiscal.getId());
        assertTrue(rereadFiscal.isFiscal());
        assertEquals("254F86-612421-9701AB-016921-3E7CD1-35", rereadFiscal.getCai());
    }

    // --- Contabilidad ---------------------------------------------------------------

    @Test
    void issuingOnCreditPostsNothing() {
        setWithoutCai(true);
        long before = journalEntryRepository.count();

        InvoiceDTO dto = issueCredit("500.00");

        assertEquals(before, journalEntryRepository.count());
        assertTrue(journalEntryRepository.findFirstBySourceTypeAndSourceId(JournalSourceType.FACTURA, dto.getId()).isEmpty());
    }

    @Test
    void aCashSalePostsNothingButRecordsThePayment() {
        setWithoutCai(true);
        long before = journalEntryRepository.count();

        InvoiceDTO dto = invoiceService.createInvoice(contado(newOrder("Hemograma", "500.00").getId(), "500.00"));

        assertEquals(InvoiceStatus.PAGADA, dto.getStatus());
        assertEquals(1, dto.getPayments().size());
        assertNotNull(dto.getPayments().get(0).getPaymentNumber());
        assertEquals(before, journalEntryRepository.count());
    }

    @Test
    void annullingAPaymentAndTheDocumentPostsNothing() {
        setWithoutCai(true);
        InvoiceDTO dto = issueCredit("500.00");
        dto = invoiceService.registerPayment(dto.getId(),
                new PaymentRequest(new BigDecimal("200.00"), PaymentMethod.EFECTIVO, null));
        invoiceService.registerPayment(dto.getId(),
                new PaymentRequest(new BigDecimal("100.00"), PaymentMethod.EFECTIVO, null));
        long before = journalEntryRepository.count();

        InvoiceDTO afterPaymentAnnul = invoiceService.annulPayment(dto.getId(),
                dto.getPayments().get(0).getId(), "Error de monto");
        assertEquals(0, new BigDecimal("100.00").compareTo(afterPaymentAnnul.getPaidAmount()));

        // Anular el documento anula también el pago que quedaba activo.
        InvoiceDTO annulled = invoiceService.annulInvoice(dto.getId(), "Error");
        assertEquals(InvoiceStatus.ANULADA, annulled.getStatus());
        assertEquals(before, journalEntryRepository.count());
    }

    @Test
    void aClosedPeriodDoesNotApplyToNonFiscalDocuments() {
        InvoiceDTO pending = issueCredit("300.00");  // fiscal, antes del cierre
        setWithoutCai(true);
        InvoiceDTO nonFiscalPending = issueCredit("400.00");
        accountingPeriodService.close(LocalDate.now().withDayOfMonth(1), LocalDate.now());

        // Emitir, abonar y anular un documento sin CAI dentro del período cerrado.
        InvoiceDTO issued = issueCredit("500.00");
        assertFalse(issued.isFiscal());
        invoiceService.registerPayment(nonFiscalPending.getId(),
                new PaymentRequest(new BigDecimal("100.00"), PaymentMethod.EFECTIVO, null));
        invoiceService.annulInvoice(issued.getId(), "Error");

        // La factura fiscal sí sigue bloqueada por el cierre.
        assertThrows(APIException.class, () -> invoiceService.registerPayment(pending.getId(),
                new PaymentRequest(new BigDecimal("100.00"), PaymentMethod.EFECTIVO, null)));
    }

    @Test
    void aFiscalInvoiceKeepsItsAccounting() {
        InvoiceDTO dto = issueCredit("500.00");
        dto = invoiceService.registerPayment(dto.getId(),
                new PaymentRequest(new BigDecimal("500.00"), PaymentMethod.EFECTIVO, null));
        invoiceService.annulInvoice(dto.getId(), "Error");

        Long paymentId = dto.getPayments().get(0).getId();
        assertTrue(journalEntryRepository.findFirstBySourceTypeAndSourceId(JournalSourceType.FACTURA, dto.getId()).isPresent());
        assertTrue(journalEntryRepository.findFirstBySourceTypeAndSourceId(JournalSourceType.PAGO, paymentId).isPresent());
        assertTrue(journalEntryRepository.findFirstBySourceTypeAndSourceId(JournalSourceType.ANULACION_PAGO, paymentId).isPresent());
        assertTrue(journalEntryRepository.findFirstBySourceTypeAndSourceId(JournalSourceType.ANULACION_FACTURA, dto.getId()).isPresent());
    }

    // --- Cuentas por cobrar y reportes ----------------------------------------------

    @Test
    void aPendingNonFiscalDocumentIsOwed() {
        setWithoutCai(true);
        InvoiceDTO dto = issueCredit("500.00");

        ReceivablesResponse receivables = invoiceService.getReceivables(0, 20);

        InvoiceDTO row = receivables.getContent().stream()
                .filter(i -> i.getId().equals(dto.getId())).findFirst().orElseThrow();
        assertFalse(row.isFiscal());
        assertEquals(0, new BigDecimal("500.00").compareTo(row.getBalance()));
        assertEquals(0, new BigDecimal("500.00").compareTo(receivables.getTotalReceivable()));
        assertEquals(0, new BigDecimal("500.00").compareTo(
                invoiceService.getCustomerStatement(customer.getId()).getBalance()));
    }

    @Test
    void theSalesRegisterIsFiscalOnly() {
        InvoiceDTO first = issueCredit("100.00");
        InvoiceDTO second = issueCredit("200.00");
        setWithoutCai(true);
        issueCredit("300.00");

        SalesRegisterDTO register = analyticsReportService.getSalesDetail(
                LocalDate.now().minusDays(1), LocalDate.now());

        List<String> numbers = register.getRows().stream().map(SalesRegisterDTO.Row::getInvoiceNumber)
                .distinct().toList();
        assertEquals(List.of(first.getInvoiceNumber(), second.getInvoiceNumber()), numbers);
        assertEquals(0, new BigDecimal("300.00").compareTo(register.getTotals().getTotal()));
    }

    @Test
    void aggregatedReportsBreakOutNonFiscalSales() {
        invoiceService.createInvoice(contado(newOrder("Hemograma", "400.00").getId(), "400.00"));
        invoiceService.createInvoice(contado(newOrder("Perfil", "600.00").getId(), "600.00"));
        setWithoutCai(true);
        invoiceService.createInvoice(contado(newOrder("Glucosa", "300.00").getId(), "300.00"));
        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now();

        SalesReportDTO.Summary sales = analyticsReportService.getSales(from, to, "day").getSummary();
        CollectionsReportDTO.Summary collections = analyticsReportService.getCollections(from, to).getSummary();

        assertEquals(0, new BigDecimal("1300.00").compareTo(sales.getTotal()));
        assertEquals(1, sales.getNonFiscalCount());
        assertEquals(0, new BigDecimal("300.00").compareTo(sales.getNonFiscalTotal()));
        assertEquals(0, new BigDecimal("1300.00").compareTo(collections.getTotal()));
        assertEquals(0, new BigDecimal("300.00").compareTo(collections.getNonFiscalTotal()));
    }
}
