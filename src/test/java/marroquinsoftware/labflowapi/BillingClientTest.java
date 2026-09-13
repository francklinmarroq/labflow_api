package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolationException;
import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.BillingClientDTO;
import marroquinsoftware.labflowapi.payload.BillingClientResponse;
import marroquinsoftware.labflowapi.payload.InvoiceDTO;
import marroquinsoftware.labflowapi.payload.InvoiceRequest;
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
 * El catálogo de clientes de facturación: qué lo hace válido, qué lo hace único
 * y cuándo una ficha ya no se puede borrar.
 *
 * <p>Lo que se está protegiendo es el documento fiscal: el RTN es con lo que la
 * empresa deduce el gasto, así que no puede estar repetido ni desaparecer de
 * debajo de una factura ya emitida.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({BillingClientServiceImp.class, InvoiceServiceImp.class, JournalServiceImp.class,
        AccountSeeder.class, CaiNumberService.class, AgeDiscountCalculator.class,
        InvoiceTotalsCalculator.class, AmountInWordsConverter.class,
        TenantIdentifierResolver.class, BillingClientTest.TestBeans.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class BillingClientTest {

    static class TestBeans {
        @Bean org.modelmapper.ModelMapper modelMapper() { return new org.modelmapper.ModelMapper(); }
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
    }

    @PersistenceContext EntityManager entityManager;

    @Autowired BillingClientService billingClientService;
    @Autowired InvoiceService invoiceService;
    @Autowired AccountSeeder accountSeeder;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired TestRepository testRepository;
    @Autowired LabOrderRepository labOrderRepository;
    @Autowired InvoiceRepository invoiceRepository;

    private Customer customer;

    @BeforeEach
    void setUp() {
        Laboratory laboratory = newLaboratory("Laboratorio de Prueba");
        TenantContext.setLaboratoryId(laboratory.getId());
        accountSeeder.seedDefaultAccounts();

        customer = new Customer();
        customer.setName("Paciente de Prueba");
        customer.setAgeInDays(30 * 365);
        customer = customerRepository.save(customer);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    // --- Utilidades ---

    private Laboratory newLaboratory(String name) {
        Laboratory laboratory = new Laboratory();
        laboratory.setName(name);
        laboratory.setRtn("08011999123456");
        laboratory.setCai1("254F86-612421-9701AB-016921-3E7CD1-35");
        laboratory.setCai1ExpirationDate(LocalDate.now().plusMonths(6));
        laboratory.setCai1RangeFrom("000-001-01-00000001");
        laboratory.setCai1RangeTo("000-001-01-00000100");
        return laboratoryRepository.save(laboratory);
    }

    private BillingClientDTO newClient(String name, String rtn) {
        return billingClientService.createBillingClient(
                new BillingClientDTO(null, name, rtn, null, null, null));
    }

    private LabOrder newOrder() {
        marroquinsoftware.labflowapi.model.Test test = new marroquinsoftware.labflowapi.model.Test();
        test.setName("Hemograma");
        test.setPrice(new BigDecimal("500.00"));
        test = testRepository.save(test);

        LabOrder order = new LabOrder();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        List<LabTest> labTests = new ArrayList<>();
        LabTest labTest = new LabTest();
        labTest.setOrder(order);
        labTest.setTest(test);
        labTests.add(labTest);
        order.setTests(labTests);
        return labOrderRepository.save(order);
    }

    private InvoiceDTO invoiceTo(Long billingClientId) {
        return invoiceService.createInvoice(new InvoiceRequest(
                newOrder().getId(), SaleCondition.CREDITO, billingClientId, null, null, null, null, null));
    }

    // --- Alta ---

    @Test
    void createsAClientWithNameAndRtn() {
        BillingClientDTO created = newClient("Aseguradora del Valle S. de R.L.", "08019012345678");

        assertNotNull(created.getId());
        assertEquals("Aseguradora del Valle S. de R.L.", created.getName());
        assertEquals("08019012345678", created.getRtn());

        BillingClientResponse listed = billingClientService.getAllBillingClients(0, 50, "name", "ASC");
        assertEquals(1, listed.getTotalElements());
    }

    @Test
    void aClientWithoutRtnIsRefused() {
        assertThrows(ConstraintViolationException.class,
                () -> newClient("Empresa Sin RTN", null));
        // Un RTN en blanco es lo mismo que ninguno: se normaliza a nulo y la
        // entidad lo rechaza igual.
        assertThrows(ConstraintViolationException.class,
                () -> newClient("Empresa Sin RTN", "   "));
    }

    // --- Unicidad del RTN ---

    @Test
    void aDuplicateRtnIsRefusedNamingTheClientThatHasIt() {
        newClient("Aseguradora del Valle", "08019012345678");

        APIException error = assertThrows(APIException.class,
                () -> newClient("Otra Empresa", "08019012345678"));

        // Sin el nombre de la ficha que ya lo tiene, quien atiende no sabe a
        // dónde ir; por eso no se deja reventar la restricción única a secas.
        assertTrue(error.getMessage().contains("Aseguradora del Valle"),
                "El rechazo debe nombrar al cliente que ya tiene el RTN, decía: " + error.getMessage());

        assertEquals(1, billingClientService.getAllBillingClients(0, 50, "name", "ASC").getTotalElements());
    }

    @Test
    void editingAClientOntoAnotherRtnIsRefused() {
        BillingClientDTO first = newClient("Aseguradora del Valle", "08019012345678");
        BillingClientDTO second = newClient("Transportes del Norte", "08019087654321");

        assertThrows(APIException.class, () -> billingClientService.updateBillingClient(
                new BillingClientDTO(null, second.getName(), first.getRtn(), null, null, null),
                second.getId()));

        // Pero conservar su propio RTN al editar el nombre sí se permite.
        BillingClientDTO renamed = billingClientService.updateBillingClient(
                new BillingClientDTO(null, "Transportes del Norte S.A.", second.getRtn(), null, null, null),
                second.getId());
        assertEquals("Transportes del Norte S.A.", renamed.getName());
    }

    /**
     * La unicidad del RTN es POR laboratorio: dos laboratorios distintos pueden
     * tener cada uno una ficha con el mismo RTN, y ninguno ve la del otro.
     *
     * <p>La ficha del segundo laboratorio se inserta con SQL nativo a propósito.
     * El tenant de una sesión de Hibernate queda fijado cuando la sesión se abre,
     * y en un {@code @DataJpaTest} toda la prueba corre dentro de una sola: mover
     * el {@link TenantContext} a mitad de camino no cambia por cuál laboratorio
     * filtra. El SQL nativo se salta el filtro (ver AGENTS.md), que es justo lo
     * que hace falta para poner una fila del otro laboratorio y comprobar las dos
     * cosas: que la restricción única la acepta —es compuesta, no sobre el RTN
     * solo— y que desde acá sigue sin verse.
     */
    @Test
    void theSameRtnIsAcceptedInAnotherLaboratory() {
        BillingClientDTO mine = newClient("Aseguradora del Valle", "08019012345678");
        Laboratory other = newLaboratory("Otro Laboratorio");

        entityManager.createNativeQuery("""
                        insert into billing_clients (laboratory_id, name, rtn)
                        values (:laboratoryId, :name, :rtn)
                        """)
                .setParameter("laboratoryId", other.getId())
                .setParameter("name", "Aseguradora del Valle")
                .setParameter("rtn", "08019012345678")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // Existe en la tabla, bajo el otro laboratorio...
        assertEquals(1L, ((Number) entityManager.createNativeQuery(
                        "select count(*) from billing_clients where laboratory_id = :laboratoryId")
                .setParameter("laboratoryId", other.getId())
                .getSingleResult()).longValue());

        // ...y desde este laboratorio no se lista ni se lee: sigue habiendo uno solo.
        BillingClientResponse listed = billingClientService.getAllBillingClients(0, 50, "name", "ASC");
        assertEquals(1, listed.getTotalElements());
        assertEquals(mine.getId(), listed.getContent().get(0).getId());
    }

    // --- Baja ---

    @Test
    void aClientWithInvoicesCannotBeDeleted() {
        BillingClientDTO client = newClient("Aseguradora del Valle", "08019012345678");
        invoiceTo(client.getId());

        APIException error = assertThrows(APIException.class,
                () -> billingClientService.deleteBillingClient(client.getId()));
        assertTrue(error.getMessage().contains("facturas"),
                "El rechazo debe decir que tiene facturas, decía: " + error.getMessage());

        assertNotNull(billingClientService.getBillingClient(client.getId()));
    }

    @Test
    void anAnnulledInvoiceStillBlocksTheDeletion() {
        BillingClientDTO client = newClient("Aseguradora del Valle", "08019012345678");
        InvoiceDTO invoice = invoiceTo(client.getId());
        invoiceService.annulInvoice(invoice.getId(), "Se facturó a quien no era");

        // La factura anulada sigue siendo un documento fiscal que nombra al
        // cliente: borrarlo la dejaría apuntando a la nada.
        assertThrows(APIException.class, () -> billingClientService.deleteBillingClient(client.getId()));
    }

    @Test
    void aClientThatWasNeverInvoicedIsDeleted() {
        BillingClientDTO client = newClient("Empresa Recién Registrada", "08019011112222");

        billingClientService.deleteBillingClient(client.getId());

        assertEquals(0, billingClientService.getAllBillingClients(0, 50, "name", "ASC").getTotalElements());
    }

    // --- La edición no reescribe lo ya emitido ---

    @Test
    void renamingAClientDoesNotRewriteAnIssuedInvoice() {
        BillingClientDTO client = newClient("Aseguradora del Valle", "08019012345678");
        InvoiceDTO invoice = invoiceTo(client.getId());

        billingClientService.updateBillingClient(
                new BillingClientDTO(null, "Aseguradora del Valle S.A. de C.V.", "08019099999999",
                        null, null, null),
                client.getId());

        InvoiceDTO reread = invoiceService.getInvoice(invoice.getId());
        assertEquals("Aseguradora del Valle", reread.getCustomerName());
        assertEquals("08019012345678", reread.getCustomerRtn());
        // El vínculo sí sigue apuntando a la ficha, que ahora se llama distinto.
        assertEquals(client.getId(), reread.getBillingClientId());
        assertEquals("Aseguradora del Valle S.A. de C.V.",
                billingClientService.getBillingClient(client.getId()).getName());
    }
}
