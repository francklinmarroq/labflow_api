package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.*;
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
 * Facturas de varias órdenes, de exámenes sueltos y de conceptos libres: líneas
 * agrupadas con cantidad, descuento por edad por orden, destinatario sin orden,
 * la factura compartida vista desde cada orden (candado, cancelación, anulación),
 * las órdenes pendientes de facturar, los filtros por orden y etiqueta, y el
 * reporte de ventas por examen.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({LabTestServiceImp.class, LabOrderServiceImp.class, OrderTagServiceImp.class,
        ReferringPhysicianServiceImp.class, TestMethodServiceImp.class,
        TestRunServiceImp.class, InvoiceServiceImp.class, JournalServiceImp.class,
        AccountSeeder.class, CaiNumberService.class, AgeDiscountCalculator.class,
        InvoiceTotalsCalculator.class, AmountInWordsConverter.class, ReferralServiceImp.class,
        AnalyticsReportServiceImp.class, TenantIdentifierResolver.class, InvoiceCompositionTest.TestBeans.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class InvoiceCompositionTest {

    /** Lo mínimo para levantar los servicios, igual que en OrderTestLockTest. */
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

    @Autowired InvoiceService invoiceService;
    @Autowired LabTestService labTestService;
    @Autowired LabOrderService labOrderService;
    @Autowired AnalyticsReportService analyticsReportService;
    @Autowired AccountSeeder accountSeeder;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired BillingClientRepository billingClientRepository;
    @Autowired TestRepository testRepository;
    @Autowired LabOrderRepository labOrderRepository;
    @Autowired OrderTagRepository orderTagRepository;
    @Autowired JournalEntryRepository journalEntryRepository;

    private Customer young;
    private Customer elder;      // tercera edad: 10 %
    private Customer veryOld;    // cuarta edad: 20 %
    private BillingClient company;
    private marroquinsoftware.labflowapi.model.Test hemograma;   // L 500
    private marroquinsoftware.labflowapi.model.Test glucosa;     // L 150

    @BeforeEach
    void setUp() {
        Laboratory laboratory = new Laboratory();
        laboratory.setName("Laboratorio de Prueba");
        laboratory.setRtn("08011999123456");
        laboratory.setCai1("254F86-612421-9701AB-016921-3E7CD1-35");
        laboratory.setCai1ExpirationDate(LocalDate.now().plusMonths(6));
        laboratory.setCai1RangeFrom("000-001-01-00000001");
        laboratory.setCai1RangeTo("000-001-01-00000100");
        laboratory.setThirdAgeMinYears(60);
        laboratory.setThirdAgeDiscountPercent(new BigDecimal("10.00"));
        laboratory.setFourthAgeMinYears(80);
        laboratory.setFourthAgeDiscountPercent(new BigDecimal("20.00"));
        laboratory = laboratoryRepository.save(laboratory);
        TenantContext.setLaboratoryId(laboratory.getId());
        accountSeeder.seedDefaultAccounts();

        young = patient("Ana Joven", 30);
        elder = patient("Beto Mayor", 70);
        veryOld = patient("Carla Anciana", 85);
        company = new BillingClient();
        company.setName("Empresa Constructora S.A.");
        company.setRtn("08019000111222");
        company = billingClientRepository.save(company);
        hemograma = test("Hemograma", "500.00");
        glucosa = test("Glucosa", "150.00");
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    // --- Utilidades ---------------------------------------------------------------

    private Customer patient(String name, int years) {
        Customer c = new Customer();
        c.setName(name);
        c.setSex(Sex.FEMALE);
        c.setAgeInDays(years * 365);
        return customerRepository.save(c);
    }

    private marroquinsoftware.labflowapi.model.Test test(String name, String price) {
        marroquinsoftware.labflowapi.model.Test t = new marroquinsoftware.labflowapi.model.Test();
        t.setName(name);
        t.setPrice(new BigDecimal(price));
        return testRepository.save(t);
    }

    private LabOrder order(Customer patient, marroquinsoftware.labflowapi.model.Test... tests) {
        LabOrder order = new LabOrder();
        order.setCustomer(patient);
        order.setStatus(OrderStatus.PENDING);
        List<LabTest> labTests = new ArrayList<>();
        for (marroquinsoftware.labflowapi.model.Test t : tests) {
            LabTest lt = new LabTest();
            lt.setOrder(order);
            lt.setTest(t);
            labTests.add(lt);
        }
        order.setTests(labTests);
        return labOrderRepository.save(order);
    }

    private static InvoiceRecipientDTO toCompany(BillingClient client) {
        return new InvoiceRecipientDTO(InvoiceRecipientType.BILLING_CLIENT, null, client.getId(), null, null);
    }

    private static InvoiceRecipientDTO toPatient(Customer patient) {
        return new InvoiceRecipientDTO(InvoiceRecipientType.PATIENT, patient.getId(), null, null, null);
    }

    private static InvoiceRecipientDTO toFinalConsumer(String name) {
        return new InvoiceRecipientDTO(InvoiceRecipientType.FINAL_CONSUMER, null, null, name, null);
    }

    /** Factura a crédito con la composición nueva. */
    private InvoiceDTO issue(List<Long> orderIds, List<InvoiceTestLineRequest> tests,
                             List<InvoiceConceptRequest> concepts, InvoiceRecipientDTO recipient,
                             List<InvoiceItemPriceDTO> prices) {
        InvoiceRequest request = new InvoiceRequest();
        request.setOrderIds(orderIds);
        request.setTests(tests);
        request.setConcepts(concepts);
        request.setRecipient(recipient);
        request.setItemPrices(prices);
        request.setSaleCondition(SaleCondition.CREDITO);
        return invoiceService.createInvoice(request);
    }

    private InvoiceDTO issueOrders(InvoiceRecipientDTO recipient, LabOrder... orders) {
        return issue(java.util.Arrays.stream(orders).map(LabOrder::getId).toList(), null, null, recipient, null);
    }

    private static InvoiceItemDTO line(InvoiceDTO invoice, String name) {
        return invoice.getItems().stream().filter(i -> i.getTestName().equals(name)).findFirst().orElseThrow();
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "se esperaba " + expected + " y fue " + actual);
    }

    private String nextInvoiceNumberProbe() {
        // El siguiente número que daría el CAI: sirve para comprobar que un rechazo
        // no gastó ninguno.
        return issue(null, null, List.of(new InvoiceConceptRequest("Sonda", BigDecimal.ONE, BigDecimal.TEN)),
                toFinalConsumer("Probe"), null).getInvoiceNumber();
    }

    // --- Composición y agrupación --------------------------------------------------

    @Test
    void ordersOfSeveralPatientsBecomeOneInvoiceWithGroupedLines() {
        LabOrder a = order(young, hemograma, glucosa);
        LabOrder b = order(elder, hemograma);
        LabOrder c = order(veryOld, hemograma);

        InvoiceDTO invoice = issueOrders(toCompany(company), a, b, c);

        assertEquals(2, invoice.getItems().size(), "las unidades del mismo examen van a una línea");
        assertAmount("3", line(invoice, "Hemograma").getQuantity());
        assertAmount("1500.00", line(invoice, "Hemograma").getAmount());
        assertAmount("1", line(invoice, "Glucosa").getQuantity());
        assertEquals(3, invoice.getOrders().size());
        assertEquals(List.of("Ana Joven", "Beto Mayor", "Carla Anciana"),
                invoice.getOrders().stream().map(InvoiceOrderDTO::getPatientName).toList());
        assertNull(invoice.getOrderId(), "con varias órdenes no hay una orden única");
        assertNull(invoice.getPatientName(), "con varios pacientes la factura los lista por orden");
        assertEquals("Empresa Constructora S.A.", invoice.getCustomerName());
        for (LabOrder o : List.of(a, b, c)) {
            assertEquals(invoice.getInvoiceNumber(), invoiceService.previewInvoice(o.getId()).getExistingInvoiceNumber(),
                    "cada orden ve la factura común");
        }
    }

    @Test
    void tenLooseHemogramsAreOneLineOfQuantityTen() {
        InvoiceDTO invoice = issue(null, List.of(new InvoiceTestLineRequest(hemograma.getId(), 10)), null,
                toFinalConsumer("Juan Pérez"), null);

        assertEquals(1, invoice.getItems().size());
        assertAmount("10", invoice.getItems().get(0).getQuantity());
        assertAmount("500.00", invoice.getItems().get(0).getPrice());
        assertAmount("5000.00", invoice.getItems().get(0).getAmount());
        assertAmount("5000.00", invoice.getTotal());
    }

    @Test
    void specialPricePerExamAppliesToEveryUnit() {
        LabOrder a = order(young, hemograma);
        LabOrder b = order(young, hemograma);

        InvoiceDTO invoice = issue(List.of(a.getId(), b.getId()), null, null, null,
                List.of(new InvoiceItemPriceDTO(null, hemograma.getId(), new BigDecimal("400.00"))));

        InvoiceItemDTO hemo = line(invoice, "Hemograma");
        assertAmount("2", hemo.getQuantity());
        assertAmount("400.00", hemo.getPrice());
        assertAmount("500.00", hemo.getListPrice(), "conserva el precio de catálogo");
        assertAmount("800.00", invoice.getTotal());
        assertAmount("200.00", invoice.getItemDiscountAmount());
    }

    private static void assertAmount(String expected, BigDecimal actual, String message) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), message);
    }

    @Test
    void specialPriceAboveCatalogIsRejectedWithoutSpendingACaiNumber() {
        LabOrder a = order(young, hemograma);

        APIException ex = assertThrows(APIException.class, () -> issue(List.of(a.getId()), null, null, null,
                List.of(new InvoiceItemPriceDTO(null, hemograma.getId(), new BigDecimal("600.00")))));
        assertTrue(ex.getMessage().contains("Hemograma"), ex.getMessage());
        assertEquals("000-001-01-00000001", nextInvoiceNumberProbe());
    }

    @Test
    void invoiceFromScratchWithAnExamAndAConceptToAFinalConsumer() {
        InvoiceDTO invoice = issue(null, List.of(new InvoiceTestLineRequest(glucosa.getId(), 1)),
                List.of(new InvoiceConceptRequest("Toma de muestra a domicilio", BigDecimal.ONE, new BigDecimal("150"))),
                toFinalConsumer("Juan Pérez"), null);

        assertEquals(2, invoice.getItems().size());
        assertEquals(InvoiceItemType.CONCEPTO, line(invoice, "Toma de muestra a domicilio").getItemType());
        assertAmount("300.00", invoice.getTotal());
        assertTrue(invoice.getOrders().isEmpty(), "no queda asociada a ninguna orden");
        assertNull(invoice.getCustomerId(), "un consumidor final no es un paciente");
        assertEquals("Juan Pérez", invoice.getCustomerName());
        assertTrue(journalEntryRepository.findFirstBySourceTypeAndSourceId(JournalSourceType.FACTURA, invoice.getId())
                .isPresent(), "lleva su asiento como cualquier factura");
    }

    @Test
    void emptyInvoiceAndInvalidConceptsAreRejected() {
        APIException empty = assertThrows(APIException.class,
                () -> issue(null, null, null, toFinalConsumer("X"), null));
        assertTrue(empty.getMessage().contains("al menos una línea"), empty.getMessage());

        APIException noDescription = assertThrows(APIException.class, () -> issue(null, null,
                List.of(new InvoiceConceptRequest("  ", BigDecimal.ONE, BigDecimal.TEN)), toFinalConsumer("X"), null));
        assertTrue(noDescription.getMessage().contains("concepto 1"), noDescription.getMessage());

        assertThrows(APIException.class, () -> issue(null, null,
                List.of(new InvoiceConceptRequest("Envío", BigDecimal.ONE, BigDecimal.ZERO)), toFinalConsumer("X"), null));
        assertThrows(APIException.class, () -> issue(null, null,
                List.of(new InvoiceConceptRequest("Envío", BigDecimal.ONE, BigDecimal.TEN)), toFinalConsumer("  "), null));
        assertEquals("000-001-01-00000001", nextInvoiceNumberProbe(), "ningún rechazo gastó un número");
    }

    @Test
    void anAlreadyInvoicedOrderRejectsTheWholeInvoice() {
        LabOrder a = order(young, hemograma);
        LabOrder b = order(elder, glucosa);
        InvoiceDTO first = issueOrders(null, a);

        APIException ex = assertThrows(APIException.class, () -> issueOrders(toCompany(company), b, a));
        assertTrue(ex.getMessage().contains(first.getInvoiceNumber()), ex.getMessage());
        assertNull(invoiceService.previewInvoice(b.getId()).getExistingInvoiceId(), "b no quedó facturada");
        assertEquals("000-001-01-00000002", nextInvoiceNumberProbe(), "el rechazo no gastó un número");
    }

    @Test
    void severalPatientsWithoutRecipientIsRejected() {
        LabOrder a = order(young, hemograma);
        LabOrder b = order(elder, glucosa);

        APIException ex = assertThrows(APIException.class, () -> issueOrders(null, a, b));
        assertTrue(ex.getMessage().contains("a nombre de quién"), ex.getMessage());
    }

    @Test
    void singleOrderInvoiceIsUnchanged() {
        LabOrder a = order(young, hemograma, glucosa);

        InvoiceDTO invoice = invoiceService.createInvoice(
                new InvoiceRequest(a.getId(), SaleCondition.CREDITO, null, null, null, null, null, null));

        assertEquals(a.getId(), invoice.getOrderId());
        assertEquals(a.getOrderNumber(), invoice.getOrderNumber());
        assertEquals("Ana Joven", invoice.getCustomerName());
        assertEquals("Ana Joven", invoice.getPatientName());
        assertEquals(young.getId(), invoice.getCustomerId());
        assertTrue(invoice.getItems().stream().allMatch(i -> i.getQuantity().compareTo(BigDecimal.ONE) == 0));
        assertAmount("650.00", invoice.getTotal());
    }

    // --- Descuento por edad ---------------------------------------------------------

    @Test
    void ageDiscountIsComputedPerOrder() {
        LabOrder elderOrder = order(elder, hemograma);     // 500 al 10 %
        LabOrder youngOrder = order(young, glucosa);       // 150 sin descuento

        InvoiceDTO invoice = issueOrders(toCompany(company), elderOrder, youngOrder);

        assertAmount("50.00", invoice.getDiscountAmount());
        assertEquals(AgeDiscountKind.THIRD_AGE, invoice.getDiscountKind());
        assertAmount("10.00", invoice.getDiscountPercent());
        assertAmount("600.00", invoice.getTotal());
        InvoiceOrderDTO elderGroup = invoice.getOrders().get(0);
        assertAmount("50.00", elderGroup.getAgeDiscountAmount());
        assertAmount("0", invoice.getOrders().get(1).getAgeDiscountAmount());
    }

    @Test
    void mixedTiersShowOnlyTheAmount() {
        LabOrder elderOrder = order(elder, hemograma);     // 10 % de 500 = 50
        LabOrder veryOldOrder = order(veryOld, hemograma); // 20 % de 500 = 100

        InvoiceDTO invoice = issueOrders(toCompany(company), elderOrder, veryOldOrder);

        assertAmount("150.00", invoice.getDiscountAmount());
        assertNull(invoice.getDiscountKind(), "con tramos mezclados no hay un tramo único");
        assertNull(invoice.getDiscountPercent());
        assertTrue(invoice.getDiscountLabel().contains("varios tramos"), invoice.getDiscountLabel());
    }

    @Test
    void looseExamsCarryNoAgeDiscount() {
        InvoiceDTO invoice = issue(null, List.of(new InvoiceTestLineRequest(hemograma.getId(), 1)), null,
                toPatient(veryOld), null);

        assertAmount("0", invoice.getDiscountAmount());
        assertAmount("500.00", invoice.getTotal());
        assertEquals(veryOld.getId(), invoice.getCustomerId(), "a nombre de un paciente, apunta a él");
    }

    @Test
    void draftPreviewMatchesTheIssuedInvoice() {
        LabOrder a = order(elder, hemograma, glucosa);
        LabOrder b = order(young, hemograma);
        InvoiceDraftRequest draft = new InvoiceDraftRequest(List.of(a.getId(), b.getId()),
                List.of(new InvoiceTestLineRequest(glucosa.getId(), 2)),
                List.of(new InvoiceConceptRequest("Envío", BigDecimal.ONE, new BigDecimal("75"))),
                toCompany(company), List.of(new InvoiceItemPriceDTO(null, glucosa.getId(), new BigDecimal("120"))),
                null);

        InvoiceDraftPreviewDTO preview = invoiceService.previewDraft(draft);
        InvoiceDTO invoice = issue(draft.getOrderIds(), draft.getTests(), draft.getConcepts(), draft.getRecipient(),
                draft.getItemPrices());

        assertEquals(preview.getItems().stream().map(i -> i.getTestName() + "×" + i.getQuantity().stripTrailingZeros()
                        + "=" + i.getAmount()).toList(),
                invoice.getItems().stream().map(i -> i.getTestName() + "×" + i.getQuantity().stripTrailingZeros()
                        + "=" + i.getAmount()).toList());
        assertAmount(preview.getSubtotal().toPlainString(), invoice.getSubtotal());
        assertAmount(preview.getDiscountAmount().toPlainString(), invoice.getDiscountAmount());
        assertAmount(preview.getTotal().toPlainString(), invoice.getTotal());
        assertAmount("3", line(invoice, "Glucosa").getQuantity());
    }

    // --- La factura compartida desde cada orden -------------------------------------

    @Test
    void annullingASharedInvoiceReleasesEveryOrder() {
        LabOrder a = order(young, hemograma);
        LabOrder b = order(elder, glucosa);
        InvoiceDTO invoice = issueOrders(toCompany(company), a, b);

        APIException locked = assertThrows(APIException.class,
                () -> labTestService.addTestToOrder(b.getId(), requesting(hemograma)));
        assertTrue(locked.getMessage().contains(invoice.getInvoiceNumber()), locked.getMessage());

        invoiceService.annulInvoice(invoice.getId(), "Error");

        List<Long> pending = invoiceService.getUninvoicedOrders(0, 50, null, null, null).getContent().stream()
                .map(UninvoicedOrderDTO::getOrderId).toList();
        assertTrue(pending.containsAll(List.of(a.getId(), b.getId())), "las dos vuelven a pendientes");
        assertDoesNotThrow(() -> labTestService.addTestToOrder(b.getId(), requesting(hemograma)));
    }

    @Test
    void cancellingAnOrderOfASharedInvoiceIsRejected() {
        LabOrder a = order(young, hemograma);
        LabOrder b = order(elder, glucosa);
        InvoiceDTO invoice = issueOrders(toCompany(company), a, b);

        APIException ex = assertThrows(APIException.class, () -> labOrderService.cancelOrder(a.getId(), "No vino"));
        assertTrue(ex.getMessage().contains(invoice.getInvoiceNumber()), ex.getMessage());
        assertEquals(InvoiceStatus.PENDIENTE, invoiceService.getInvoice(invoice.getId()).getStatus());
        assertEquals(OrderStatus.PENDING, labOrderRepository.findById(a.getId()).orElseThrow().getStatus());
    }

    private LabTestDTO requesting(marroquinsoftware.labflowapi.model.Test t) {
        LabTestDTO dto = new LabTestDTO();
        dto.setTestId(t.getId());
        return dto;
    }

    @Test
    void uninvoicedListExcludesCancelledEmptyAndInvoicedOrders() {
        LabOrder plain = order(young, hemograma);
        LabOrder invoiced = order(young, glucosa);
        issueOrders(null, invoiced);
        LabOrder cancelled = order(young, hemograma);
        cancelled.setStatus(OrderStatus.CANCELLED);
        labOrderRepository.save(cancelled);
        LabOrder empty = order(young);
        LabOrder reopened = order(elder, glucosa);
        InvoiceDTO annulled = issueOrders(null, reopened);
        invoiceService.annulInvoice(annulled.getId(), "Error");

        List<UninvoicedOrderDTO> pending = invoiceService.getUninvoicedOrders(0, 50, null, null, null).getContent();
        List<Long> ids = pending.stream().map(UninvoicedOrderDTO::getOrderId).toList();

        assertTrue(ids.contains(plain.getId()));
        assertTrue(ids.contains(reopened.getId()), "su única factura se anuló");
        assertFalse(ids.contains(invoiced.getId()));
        assertFalse(ids.contains(cancelled.getId()));
        assertFalse(ids.contains(empty.getId()));
        UninvoicedOrderDTO row = pending.stream().filter(p -> p.getOrderId().equals(plain.getId())).findFirst().orElseThrow();
        assertAmount("500.00", row.getCatalogTotal());
        assertEquals("Ana Joven", row.getCustomerName());
        assertEquals(1, invoiceService.getUninvoicedOrders(0, 50, elder.getId(), null, null).getContent().size(),
                "filtra por paciente");
    }

    // --- Listados y reportes ----------------------------------------------------------

    @Test
    void aSharedInvoiceIsFoundFromAnyOfItsOrdersAndTags() {
        OrderTag tag = new OrderTag();
        tag.setName("Convenio");
        tag.setNormalizedName("convenio");
        tag.setColor("#123456");
        tag = orderTagRepository.save(tag);
        LabOrder a = order(young, hemograma);
        LabOrder b = order(elder, glucosa);
        a.getTags().add(tag);
        b.getTags().add(tag);
        labOrderRepository.save(a);
        labOrderRepository.save(b);
        InvoiceDTO invoice = issueOrders(toCompany(company), a, b);

        for (LabOrder o : List.of(a, b)) {
            List<InvoiceDTO> found = invoiceService.getAllInvoices(0, 50, "issuedAt", "DESC", null, o.getId(),
                    null, null, null, null, null).getContent();
            assertEquals(List.of(invoice.getId()), found.stream().map(InvoiceDTO::getId).toList());
        }
        List<InvoiceDTO> byTag = invoiceService.getAllInvoices(0, 50, "issuedAt", "DESC", null, null,
                null, null, null, tag.getId(), null).getContent();
        assertEquals(1, byTag.size(), "dos órdenes con la misma etiqueta no duplican la factura");
        assertEquals(List.of("Convenio"), byTag.get(0).getTags().stream().map(OrderTagDTO::getName).toList());
    }

    @Test
    void salesByExamCountsQuantities() {
        issue(null, List.of(new InvoiceTestLineRequest(hemograma.getId(), 10)),
                List.of(new InvoiceConceptRequest("Envío", new BigDecimal("2"), new BigDecimal("50"))),
                toFinalConsumer("Juan"), null);

        SalesReportDTO report = analyticsReportService.getSales(LocalDate.now().minusDays(1), LocalDate.now(), "day");

        SalesReportDTO.TestSales hemo = report.getByTest().stream()
                .filter(t -> t.getTestName().equals("Hemograma")).findFirst().orElseThrow();
        assertEquals(10, hemo.getQty());
        assertAmount("5000.00", hemo.getAmount());
        SalesReportDTO.TestSales envio = report.getByTest().stream()
                .filter(t -> t.getTestName().equals("Envío")).findFirst().orElseThrow();
        assertEquals(2, envio.getQty());
        assertAmount("100.00", envio.getAmount());
    }

    // --- Registro de ventas detallado ---------------------------------------------

    @Test
    void salesRegisterSplitsDiscountsPerLineAndBalancesWithTheSummary() {
        // Factura 1: dos órdenes (una al 10 %) + una glucosa suelta + un concepto, a la empresa.
        LabOrder a = numbered(order(young, hemograma, glucosa), 41L);
        LabOrder b = numbered(order(elder, hemograma), 42L);
        InvoiceDTO shared = issue(List.of(a.getId(), b.getId()),
                List.of(new InvoiceTestLineRequest(glucosa.getId(), 1)),
                List.of(new InvoiceConceptRequest("Toma a domicilio", BigDecimal.ONE, new BigDecimal("200"))),
                toCompany(company), null);
        // Factura 2: sin órdenes, con rebaja del total (400 → 360).
        InvoiceRequest rebate = new InvoiceRequest();
        rebate.setConcepts(List.of(new InvoiceConceptRequest("Sonda", BigDecimal.ONE, new BigDecimal("300")),
                new InvoiceConceptRequest("Envío", BigDecimal.ONE, new BigDecimal("100"))));
        rebate.setRecipient(toFinalConsumer("Juan Pérez"));
        rebate.setSaleCondition(SaleCondition.CREDITO);
        rebate.setTotal(new BigDecimal("360"));
        InvoiceDTO loose = invoiceService.createInvoice(rebate);
        // Factura 3: anulada.
        LabOrder c = numbered(order(veryOld, hemograma), 43L);
        InvoiceDTO annulled = issueOrders(toPatient(veryOld), c);
        invoiceService.annulInvoice(annulled.getId(), "Error");

        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now();
        SalesRegisterDTO register = analyticsReportService.getSalesDetail(from, to);
        SalesReportDTO summary = analyticsReportService.getSales(from, to, "day");

        assertAmount(summary.getSummary().getTotal().toPlainString(), register.getTotals().getTotal());
        assertAmount("1810.00", register.getTotals().getTotal());   // 1450 + 360

        List<SalesRegisterDTO.Row> sharedRows = rowsOf(register, shared);
        assertEquals(3, sharedRows.size());
        assertEquals(List.of(41L, 42L), sharedRows.get(0).getOrderNumbers());
        SalesRegisterDTO.Row hemo = rowOf(sharedRows, "Hemograma");
        assertAmount("2", hemo.getQuantity());
        assertAmount("1000.00", hemo.getSubtotal());
        assertAmount("50.00", hemo.getDiscount());
        assertAmount("950.00", hemo.getTotal());
        assertAmount("0", rowOf(sharedRows, "Glucosa").getDiscount());
        assertAmount("0", rowOf(sharedRows, "Toma a domicilio").getDiscount());
        assertAmount(shared.getTotal().toPlainString(), sum(sharedRows));
        assertAmount("950.00", hemo.getExempt());
        assertAmount("0", hemo.getTaxed15());
        assertAmount("0", hemo.getTax18());

        List<SalesRegisterDTO.Row> looseRows = rowsOf(register, loose);
        assertTrue(looseRows.get(0).getOrderNumbers().isEmpty(), "sin órdenes, el campo va vacío");
        assertAmount("270.00", rowOf(looseRows, "Sonda").getTotal());
        assertAmount("90.00", rowOf(looseRows, "Envío").getTotal());

        List<SalesRegisterDTO.Row> annulledRows = rowsOf(register, annulled);
        assertEquals(1, annulledRows.size(), "la anulada aparece para que el correlativo quede completo");
        SalesRegisterDTO.Row zero = annulledRows.get(0);
        assertEquals(InvoiceStatus.ANULADA, zero.getStatus());
        assertEquals(List.of(43L), zero.getOrderNumbers());
        for (BigDecimal v : List.of(zero.getQuantity(), zero.getUnitPrice(), zero.getSubtotal(), zero.getDiscount(),
                zero.getExempt(), zero.getTotal())) {
            assertAmount("0", v);
        }

        List<String> numbers = register.getRows().stream().map(SalesRegisterDTO.Row::getInvoiceNumber).toList();
        assertEquals(numbers.stream().sorted().toList(), numbers, "por fecha y número");
    }

    @Test
    void salesRegisterOutsideTheRangeIsEmpty() {
        issueOrders(toPatient(young), order(young, hemograma));

        SalesRegisterDTO register = analyticsReportService.getSalesDetail(
                LocalDate.now().minusDays(10), LocalDate.now().minusDays(3));

        assertTrue(register.getRows().isEmpty());
        assertAmount("0", register.getTotals().getTotal());
    }

    private LabOrder numbered(LabOrder order, long number) {
        order.setOrderNumber(number);
        return labOrderRepository.save(order);
    }

    private static List<SalesRegisterDTO.Row> rowsOf(SalesRegisterDTO register, InvoiceDTO invoice) {
        return register.getRows().stream().filter(r -> r.getInvoiceId().equals(invoice.getId())).toList();
    }

    private static SalesRegisterDTO.Row rowOf(List<SalesRegisterDTO.Row> rows, String description) {
        return rows.stream().filter(r -> r.getDescription().equals(description)).findFirst().orElseThrow();
    }

    private static BigDecimal sum(List<SalesRegisterDTO.Row> rows) {
        return rows.stream().map(SalesRegisterDTO.Row::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
