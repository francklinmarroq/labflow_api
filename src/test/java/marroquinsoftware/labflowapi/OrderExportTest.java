package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.Invoice;
import marroquinsoftware.labflowapi.model.InvoiceOrder;
import marroquinsoftware.labflowapi.model.InvoiceStatus;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.SaleCondition;
import marroquinsoftware.labflowapi.payload.OrderExportRowDTO;
import marroquinsoftware.labflowapi.payload.OrderExportRowDTO.TotalSource;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.InvoiceOrderRepository;
import marroquinsoftware.labflowapi.repositories.InvoiceRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import marroquinsoftware.labflowapi.service.AgeDiscountCalculator;
import marroquinsoftware.labflowapi.service.InvoiceService;
import marroquinsoftware.labflowapi.service.LabOrderService;
import marroquinsoftware.labflowapi.service.LabOrderServiceImp;
import marroquinsoftware.labflowapi.service.OrderTagServiceImp;
import marroquinsoftware.labflowapi.service.QuoteServiceImp;
import marroquinsoftware.labflowapi.service.ReferringPhysicianServiceImp;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import marroquinsoftware.labflowapi.tenant.TenantIdentifierResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * La exportación de órdenes a Excel: por cada orden, sus exámenes y el total a
 * pagar. Una orden con factura vigente vale lo que esa factura cobra por ella
 * (menos su descuento por edad); sin factura vigente, sus precios de catálogo.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({LabOrderServiceImp.class, QuoteServiceImp.class, AgeDiscountCalculator.class,
        OrderTagServiceImp.class, ReferringPhysicianServiceImp.class, TenantIdentifierResolver.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class OrderExportTest {

    // LabOrderServiceImp lo usa solo para anular facturas; acá las facturas se
    // arman directo por el repositorio.
    @MockitoBean InvoiceService invoiceService;

    @Autowired LabOrderService labOrderService;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired TestRepository testRepository;
    @Autowired LabOrderRepository labOrderRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired InvoiceOrderRepository invoiceOrderRepository;
    @Autowired TestEntityManager entityManager;

    private Laboratory laboratory;
    private Customer customer;
    private marroquinsoftware.labflowapi.model.Test hemograma;
    private marroquinsoftware.labflowapi.model.Test glucosa;
    private marroquinsoftware.labflowapi.model.Test orina;
    private long nextOrderNumber = 1;
    private long nextInvoiceNumber = 1;

    @BeforeEach
    void setUp() {
        laboratory = newLaboratory("Laboratorio de Prueba");
        TenantContext.setLaboratoryId(laboratory.getId());
        customer = newCustomer("Ana López");
        hemograma = newTest("Hemograma", "200.00");
        glucosa = newTest("Glucosa", "100.00");
        orina = newTest("Examen general de orina", "80.00");
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void orderWithoutInvoiceIsPricedAtCatalog() {
        LabOrder order = newOrder(hemograma, glucosa);

        OrderExportRowDTO row = exportOne(order);

        assertEquals(List.of("Hemograma", "Glucosa"), row.getTestNames());
        assertEquals(2, row.getTestCount());
        assertEquals(new BigDecimal("300.00"), row.getTotal());
        assertEquals(TotalSource.CATALOG, row.getTotalSource());
        assertNull(row.getInvoiceNumber());
        assertNull(row.getInvoiceStatus());
    }

    @Test
    void catalogTotalUsesTodaysPrices() {
        LabOrder order = newOrder(hemograma);
        hemograma.setPrice(new BigDecimal("250.00"));
        testRepository.save(hemograma);

        assertEquals(new BigDecimal("250.00"), exportOne(order).getTotal());
    }

    @Test
    void invoicedOrderIsWhatItsInvoiceChargesMinusAgeDiscount() {
        LabOrder order = newOrder(hemograma, glucosa);
        Invoice invoice = newInvoice(InvoiceStatus.PENDIENTE, Instant.now(), order, "400.00", "100.00");

        OrderExportRowDTO row = exportOne(order);

        assertEquals(new BigDecimal("300.00"), row.getTotal());
        assertEquals(TotalSource.INVOICED, row.getTotalSource());
        assertEquals(invoice.getInvoiceNumber(), row.getInvoiceNumber());
        assertEquals("PENDIENTE", row.getInvoiceStatus());
        assertEquals("Pendiente", row.getInvoiceStatusLabel());
    }

    @Test
    void annulledInvoiceDoesNotCount() {
        LabOrder order = newOrder(hemograma, glucosa);
        newInvoice(InvoiceStatus.ANULADA, Instant.now(), order, "400.00", "0");

        OrderExportRowDTO row = exportOne(order);

        assertEquals(new BigDecimal("300.00"), row.getTotal());
        assertEquals(TotalSource.CATALOG, row.getTotalSource());
        assertNull(row.getInvoiceNumber());
    }

    @Test
    void newestLiveInvoiceWinsIfThereWereTwo() {
        LabOrder order = newOrder(hemograma);
        newInvoice(InvoiceStatus.PAGADA, Instant.now().minus(2, ChronoUnit.DAYS), order, "180.00", "0");
        Invoice newest = newInvoice(InvoiceStatus.PARCIAL, Instant.now(), order, "200.00", "0");

        OrderExportRowDTO row = exportOne(order);

        assertEquals(newest.getInvoiceNumber(), row.getInvoiceNumber());
        assertEquals(new BigDecimal("200.00"), row.getTotal());
    }

    @Test
    void testsComeInTheOrderTheyWereAdded() {
        LabOrder order = newOrder(orina, hemograma, glucosa);

        OrderExportRowDTO row = exportOne(order);

        assertEquals(List.of("Examen general de orina", "Hemograma", "Glucosa"), row.getTestNames());
        assertEquals(3, row.getTestCount());
    }

    @Test
    void rowsFollowTheRequestedOrderAndSkipUnknownIds() {
        LabOrder first = newOrder(hemograma);
        LabOrder second = newOrder(glucosa);
        flushAndClear();

        List<OrderExportRowDTO> rows = labOrderService.exportOrders(
                List.of(second.getId(), 999_999L, first.getId(), second.getId()));

        assertEquals(List.of(second.getId(), first.getId()), rows.stream().map(OrderExportRowDTO::getOrderId).toList());
    }

    /**
     * La orden ajena se pasa al otro laboratorio con SQL nativo, como en
     * {@code ReferringPhysicianTest}: el tenant de la sesión de Hibernate queda
     * fijado al abrirla, y en un {@code @DataJpaTest} toda la prueba corre en una
     * sola, así que mover el {@link TenantContext} a mitad de camino no cambia con
     * qué laboratorio se guarda.
     */
    @Test
    void ordersOfAnotherLaboratoryAreLeftOut() {
        LabOrder mine = newOrder(hemograma);
        LabOrder foreign = newOrder(glucosa);
        Laboratory other = newLaboratory("Otro Laboratorio");
        for (String table : List.of("lab_orders", "lab_tests")) {
            String idColumn = table.equals("lab_orders") ? "id" : "order_id";
            entityManager.getEntityManager().createNativeQuery(
                            "update " + table + " set laboratory_id = :laboratoryId where " + idColumn + " = :orderId")
                    .setParameter("laboratoryId", other.getId())
                    .setParameter("orderId", foreign.getId())
                    .executeUpdate();
        }
        flushAndClear();

        List<OrderExportRowDTO> rows = labOrderService.exportOrders(List.of(mine.getId(), foreign.getId()));

        assertEquals(List.of(mine.getId()), rows.stream().map(OrderExportRowDTO::getOrderId).toList(),
                "la orden del otro laboratorio no aparece, ni siquiera vacía");
    }

    // --- Utilidades ---

    private OrderExportRowDTO exportOne(LabOrder order) {
        flushAndClear();
        List<OrderExportRowDTO> rows = labOrderService.exportOrders(List.of(order.getId()));
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    // Que la exportación lea de la base y no de las entidades que la prueba tiene
    // en memoria.
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Laboratory newLaboratory(String name) {
        Laboratory lab = new Laboratory();
        lab.setName(name);
        return laboratoryRepository.save(lab);
    }

    private Customer newCustomer(String name) {
        Customer c = new Customer();
        c.setName(name);
        c.setAgeInDays(30 * 365);
        return customerRepository.save(c);
    }

    private marroquinsoftware.labflowapi.model.Test newTest(String name, String price) {
        marroquinsoftware.labflowapi.model.Test test = new marroquinsoftware.labflowapi.model.Test();
        test.setName(name);
        test.setPrice(new BigDecimal(price));
        return testRepository.save(test);
    }

    private LabOrder newOrder(marroquinsoftware.labflowapi.model.Test... tests) {
        return newOrder(customer, tests);
    }

    /** Una orden con los exámenes indicados, en ese orden, creada directo por el repositorio. */
    private LabOrder newOrder(Customer patient, marroquinsoftware.labflowapi.model.Test... tests) {
        LabOrder order = new LabOrder();
        order.setOrderNumber(nextOrderNumber++);
        order.setCustomer(patient);
        order.setRequestedAt(Instant.now());
        order.setStatus(OrderStatus.PENDING);
        List<LabTest> labTests = new ArrayList<>();
        for (marroquinsoftware.labflowapi.model.Test test : tests) {
            LabTest labTest = new LabTest();
            labTest.setOrder(order);
            labTest.setTest(test);
            labTests.add(labTest);
        }
        order.setTests(labTests);
        return labOrderRepository.saveAndFlush(order);
    }

    /** Una factura que cubre la orden cobrando {@code charged} con un descuento por edad de {@code ageDiscount}. */
    private Invoice newInvoice(InvoiceStatus status, Instant issuedAt, LabOrder order, String charged,
                               String ageDiscount) {
        BigDecimal chargedAmount = new BigDecimal(charged);
        BigDecimal discount = new BigDecimal(ageDiscount);
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("000-001-01-%08d".formatted(nextInvoiceNumber++));
        invoice.setCustomer(order.getCustomer());
        invoice.setCustomerName(order.getCustomer().getName());
        invoice.setIssuedAt(issuedAt);
        invoice.setStatus(status);
        invoice.setSaleCondition(SaleCondition.CONTADO);
        invoice.setSubtotal(chargedAmount);
        invoice.setDiscountAmount(discount);
        invoice.setTotal(chargedAmount.subtract(discount));
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice = invoiceRepository.save(invoice);

        InvoiceOrder invoiceOrder = new InvoiceOrder();
        invoiceOrder.setInvoice(invoice);
        invoiceOrder.setOrder(order);
        invoiceOrder.setCustomer(order.getCustomer());
        invoiceOrder.setPatientName(order.getCustomer().getName());
        invoiceOrder.setChargedAmount(chargedAmount);
        invoiceOrder.setAgeDiscountAmount(discount);
        invoiceOrderRepository.saveAndFlush(invoiceOrder);
        return invoice;
    }
}
