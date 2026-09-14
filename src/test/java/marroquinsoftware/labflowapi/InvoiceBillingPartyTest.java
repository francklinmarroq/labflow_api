package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A nombre de quién sale la factura: el paciente de la orden, como siempre, o
 * una empresa del catálogo de clientes de facturación.
 *
 * <p>Lo que se protege acá es que el documento fiscal no pueda mentir. El nombre
 * y el RTN impresos son siempre los del destinatario y nunca se contradicen; el
 * paciente queda congelado aparte para que la factura siga diciendo de quién son
 * los exámenes; y elegir mal un cliente no puede gastar un número de CAI, que es
 * irrecuperable.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({InvoiceServiceImp.class, BillingClientServiceImp.class, JournalServiceImp.class,
        AccountSeeder.class, CaiNumberService.class, AgeDiscountCalculator.class,
        InvoiceTotalsCalculator.class, AmountInWordsConverter.class,
        TenantIdentifierResolver.class, InvoiceBillingPartyTest.TestBeans.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class InvoiceBillingPartyTest {

    static class TestBeans {
        @Bean org.modelmapper.ModelMapper modelMapper() { return new org.modelmapper.ModelMapper(); }
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
    }

    @Autowired InvoiceService invoiceService;
    @Autowired BillingClientService billingClientService;
    @Autowired AccountSeeder accountSeeder;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired TestRepository testRepository;
    @Autowired LabOrderRepository labOrderRepository;

    private Customer customer;
    private BillingClientDTO empresa;

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
        customer.setAgeInDays(30 * 365); // 30 años: sin descuento por edad
        customer.setTaxNumber("08011990111111"); // RTN del expediente
        customer = customerRepository.save(customer);

        empresa = billingClientService.createBillingClient(new BillingClientDTO(
                null, "Aseguradora del Valle S. de R.L.", "08019012345678", null, null, null));
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    // --- Utilidades ---

    /** El nombre del examen es único por laboratorio, así que cada orden lleva el suyo. */
    private int testCounter = 0;

    private LabOrder newOrder(Customer forCustomer, String price) {
        marroquinsoftware.labflowapi.model.Test test = new marroquinsoftware.labflowapi.model.Test();
        test.setName("Examen " + (++testCounter));
        test.setPrice(new BigDecimal(price));
        test = testRepository.save(test);

        LabOrder order = new LabOrder();
        order.setCustomer(forCustomer);
        order.setStatus(OrderStatus.PENDING);
        List<LabTest> labTests = new ArrayList<>();
        LabTest labTest = new LabTest();
        labTest.setOrder(order);
        labTest.setTest(test);
        labTests.add(labTest);
        order.setTests(labTests);
        return labOrderRepository.save(order);
    }

    private LabOrder newOrder() {
        return newOrder(customer, "500.00");
    }

    /** Emite a crédito, con el destinatario y el RTN a mano que se indiquen. */
    private InvoiceDTO issue(Long orderId, Long billingClientId, String typedRtn) {
        return invoiceService.createInvoice(new InvoiceRequest(
                orderId, SaleCondition.CREDITO, billingClientId, typedRtn, null, null, null, null));
    }

    private InvoiceDTO pay(InvoiceDTO invoice, String amount) {
        return invoiceService.registerPayment(invoice.getId(),
                new PaymentRequest(new BigDecimal(amount), PaymentMethod.EFECTIVO, null));
    }

    // --- 4.2 A nombre de quién sale ---

    @Test
    void anInvoiceToACompanyCarriesTheCompanyAndFreezesThePatient() {
        InvoiceDTO invoice = issue(newOrder().getId(), empresa.getId(), null);

        assertEquals("Aseguradora del Valle S. de R.L.", invoice.getCustomerName());
        assertEquals("08019012345678", invoice.getCustomerRtn());
        assertEquals(empresa.getId(), invoice.getBillingClientId());
        // La factura sigue diciendo de quién son los exámenes.
        assertEquals("Paciente de Prueba", invoice.getPatientName());
        // Y el vínculo con el paciente y con la orden no se toca.
        assertEquals(customer.getId(), invoice.getCustomerId());
    }

    @Test
    void anInvoiceWithoutABillingClientIsWhatItAlwaysWas() {
        InvoiceDTO invoice = issue(newOrder().getId(), null, null);

        assertNull(invoice.getBillingClientId());
        assertEquals("Paciente de Prueba", invoice.getCustomerName());
        // Sin RTN a mano se usa el del expediente, como antes de este cambio.
        assertEquals("08011990111111", invoice.getCustomerRtn());
        assertEquals("Paciente de Prueba", invoice.getPatientName());
    }

    @Test
    void aTypedRtnIsIgnoredForACompanyAndHonouredForAPatient() {
        InvoiceDTO toCompany = issue(newOrder().getId(), empresa.getId(), "08019099999999");
        // Un documento fiscal no puede llevar un RTN que contradiga el nombre que
        // imprime: manda la ficha del cliente.
        assertEquals("08019012345678", toCompany.getCustomerRtn());

        InvoiceDTO toPatient = issue(newOrder().getId(), null, "08019099999999");
        assertEquals("08019099999999", toPatient.getCustomerRtn());
    }

    @Test
    void anUnknownBillingClientIsRefusedWithoutConsumingACaiNumber() {
        InvoiceDTO before = issue(newOrder().getId(), null, null);

        LabOrder doomed = newOrder();
        assertThrows(ResourceNotFoundException.class, () -> issue(doomed.getId(), 999_999L, null));

        // Ni se creó la factura de esa orden...
        assertEquals(0, invoiceService.getAllInvoices(0, 50, "issuedAt", "DESC",
                null, doomed.getId(), null, null, null, null, null).getContent().size());

        // ...ni se gastó un número: el siguiente es el consecutivo del anterior, sin
        // hueco. Un número quemado deja un vacío en el rango autorizado por el SAR
        // que no se puede recuperar, así que el cliente se resuelve ANTES del CAI.
        InvoiceDTO after = issue(newOrder().getId(), empresa.getId(), null);
        assertEquals(nextOf(before.getInvoiceNumber()), after.getInvoiceNumber());
    }

    /** "000-001-01-00000007" -> "000-001-01-00000008". */
    private String nextOf(String invoiceNumber) {
        int cut = invoiceNumber.lastIndexOf('-') + 1;
        String correlative = invoiceNumber.substring(cut);
        return invoiceNumber.substring(0, cut)
                + String.format("%0" + correlative.length() + "d", Long.parseLong(correlative) + 1);
    }

    @Test
    void twoOrdersOfTheSamePatientCanGoToDifferentRecipients() {
        InvoiceDTO toCompany = issue(newOrder().getId(), empresa.getId(), null);
        InvoiceDTO toPatient = issue(newOrder().getId(), null, null);

        assertEquals(empresa.getId(), toCompany.getBillingClientId());
        assertNull(toPatient.getBillingClientId());
        // El paciente congelado es el mismo en las dos: el destinatario es una
        // propiedad de la factura, no del paciente ni de la orden.
        assertEquals("Paciente de Prueba", toCompany.getPatientName());
        assertEquals("Paciente de Prueba", toPatient.getPatientName());
    }

    // --- 4.3 El filtro del listado ---

    @Test
    void theListingFiltersByBillingClient() {
        BillingClientDTO otra = billingClientService.createBillingClient(new BillingClientDTO(
                null, "Transportes del Norte", "08019087654321", null, null, null));

        InvoiceDTO deEmpresa = issue(newOrder().getId(), empresa.getId(), null);
        issue(newOrder().getId(), otra.getId(), null);
        issue(newOrder().getId(), null, null);

        InvoiceResponse filtered = invoiceService.getAllInvoices(0, 50, "issuedAt", "DESC",
                null, null, null, null, null, null, empresa.getId());
        assertEquals(1, filtered.getContent().size());
        assertEquals(deEmpresa.getInvoiceNumber(), filtered.getContent().get(0).getInvoiceNumber());

        // Sin filtro siguen estando las tres, la del paciente incluida.
        assertEquals(3, invoiceService.getAllInvoices(0, 50, "issuedAt", "DESC",
                null, null, null, null, null, null, null).getContent().size());
    }

    @Test
    void theBillingClientFilterCombinesWithStatus() {
        InvoiceDTO pendiente = issue(newOrder().getId(), empresa.getId(), null);
        InvoiceDTO pagada = issue(newOrder().getId(), empresa.getId(), null);
        pay(pagada, "500.00");

        assertEquals(1, invoiceService.getAllInvoices(0, 50, "issuedAt", "DESC",
                InvoiceStatus.PENDIENTE, null, null, null, null, null, empresa.getId())
                .getContent().size());
        assertEquals(pendiente.getInvoiceNumber(), invoiceService.getAllInvoices(0, 50, "issuedAt", "DESC",
                InvoiceStatus.PENDIENTE, null, null, null, null, null, empresa.getId())
                .getContent().get(0).getInvoiceNumber());

        assertEquals(1, invoiceService.getAllInvoices(0, 50, "issuedAt", "DESC",
                InvoiceStatus.PAGADA, null, null, null, null, null, empresa.getId())
                .getContent().size());
    }

    // --- 4.4 Estado de cuenta y saldo por cliente ---

    @Test
    void theStatementOfACompanyRunsTheBalanceAndSkipsWhatWasAnnulled() {
        InvoiceDTO first = issue(newOrder().getId(), empresa.getId(), null);   // 500.00
        pay(first, "200.00");

        InvoiceDTO second = issue(newOrder(customer, "300.00").getId(), empresa.getId(), null);

        // Una factura anulada y un pago anulado, que no deben aparecer ni sumar.
        InvoiceDTO annulled = issue(newOrder(customer, "700.00").getId(), empresa.getId(), null);
        invoiceService.annulInvoice(annulled.getId(), "Se emitió por error");
        InvoiceDTO withAnnulledPayment = invoiceService.registerPayment(second.getId(),
                new PaymentRequest(new BigDecimal("100.00"), PaymentMethod.EFECTIVO, null));
        invoiceService.annulPayment(second.getId(),
                withAnnulledPayment.getPayments().get(0).getId(), "Cheque devuelto");

        CustomerStatementDTO statement = invoiceService.getBillingClientStatement(empresa.getId());

        assertEquals(empresa.getId(), statement.getCustomerId());
        assertEquals("Aseguradora del Valle S. de R.L.", statement.getCustomerName());
        // Dos cargos y un abono: la factura anulada y el pago anulado quedan fuera.
        assertEquals(3, statement.getRows().size());
        assertEquals(0, statement.getTotalInvoiced().compareTo(new BigDecimal("800.00")));
        assertEquals(0, statement.getTotalPaid().compareTo(new BigDecimal("200.00")));
        assertEquals(0, statement.getBalance().compareTo(new BigDecimal("600.00")));

        // Los movimientos van en orden cronológico y el saldo corre con ellos.
        List<CustomerStatementRowDTO> rows = statement.getRows();
        for (int i = 1; i < rows.size(); i++) {
            assertFalse(rows.get(i).getDate().isBefore(rows.get(i - 1).getDate()),
                    "el estado de cuenta debe ir en orden cronológico");
        }
        assertEquals(0, rows.get(rows.size() - 1).getBalance().compareTo(new BigDecimal("600.00")));
    }

    @Test
    void theBalanceByClientCountsOnlyWhatCompaniesOwe() {
        BillingClientDTO otra = billingClientService.createBillingClient(new BillingClientDTO(
                null, "Transportes del Norte", "08019087654321", null, null, null));
        BillingClientDTO alDia = billingClientService.createBillingClient(new BillingClientDTO(
                null, "Empresa al Día", "08019055556666", null, null, null));

        issue(newOrder().getId(), empresa.getId(), null);                       // 500 pendiente
        pay(issue(newOrder(customer, "300.00").getId(), empresa.getId(), null), "100.00"); // 200 parcial
        issue(newOrder(customer, "400.00").getId(), otra.getId(), null);        // 400 pendiente
        pay(issue(newOrder(customer, "250.00").getId(), alDia.getId(), null), "250.00"); // saldada
        issue(newOrder().getId(), null, null);                                  // del paciente

        List<BillingClientBalanceDTO> balances = invoiceService.getReceivablesByBillingClient();

        assertEquals(2, balances.size(), "solo los clientes con saldo abierto");

        BillingClientBalanceDTO delValle = balances.stream()
                .filter(b -> b.getBillingClientId().equals(empresa.getId()))
                .findFirst().orElseThrow(() -> new AssertionError("falta el saldo de la aseguradora"));
        assertEquals("Aseguradora del Valle S. de R.L.", delValle.getBillingClientName());
        assertEquals(2L, delValle.getInvoiceCount());
        assertEquals(0, delValle.getBalance().compareTo(new BigDecimal("700.00")));

        BillingClientBalanceDTO delNorte = balances.stream()
                .filter(b -> b.getBillingClientId().equals(otra.getId()))
                .findFirst().orElseThrow(() -> new AssertionError("falta el saldo de transportes"));
        assertEquals(1L, delNorte.getInvoiceCount());
        assertEquals(0, delNorte.getBalance().compareTo(new BigDecimal("400.00")));

        // La empresa sin saldo no aparece, y la factura del paciente no se le
        // atribuyó a ningún cliente: la suma de los saldos por cliente no la incluye.
        assertTrue(balances.stream().noneMatch(b -> b.getBillingClientId().equals(alDia.getId())));
        assertEquals(0, balances.stream()
                .map(BillingClientBalanceDTO::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(new BigDecimal("1100.00")));
    }
}
