package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.Quote;
import marroquinsoftware.labflowapi.payload.QuoteDTO;
import marroquinsoftware.labflowapi.payload.QuoteRequest;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.QuoteRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import marroquinsoftware.labflowapi.service.AgeDiscountCalculator;
import marroquinsoftware.labflowapi.service.InvoiceService;
import marroquinsoftware.labflowapi.service.LabOrderService;
import marroquinsoftware.labflowapi.service.LabOrderServiceImp;
import marroquinsoftware.labflowapi.service.OrderTagServiceImp;
import marroquinsoftware.labflowapi.service.QuoteService;
import marroquinsoftware.labflowapi.service.QuoteServiceImp;
import marroquinsoftware.labflowapi.service.ReferringPhysicianServiceImp;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import marroquinsoftware.labflowapi.tenant.TenantIdentifierResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Base de las pruebas de cotizaciones: un laboratorio nuevo por prueba (así cada
 * una ve solo sus datos, aunque no corra dentro de una transacción), un paciente
 * registrado y dos exámenes en el catálogo.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({QuoteServiceImp.class, AgeDiscountCalculator.class,
        // Para convertir cotizaciones en órdenes.
        LabOrderServiceImp.class, OrderTagServiceImp.class, ReferringPhysicianServiceImp.class,
        TenantIdentifierResolver.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
abstract class QuoteTestSupport {

    // LabOrderServiceImp lo usa solo para anular facturas; se simula para no
    // arrastrar la facturación y la contabilidad al contexto.
    @MockitoBean InvoiceService invoiceService;

    @Autowired QuoteService quoteService;
    @Autowired LabOrderService labOrderService;
    @Autowired QuoteRepository quoteRepository;
    @Autowired LabOrderRepository labOrderRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired TestRepository testRepository;

    Laboratory laboratory;
    Customer customer;
    marroquinsoftware.labflowapi.model.Test hemograma;
    marroquinsoftware.labflowapi.model.Test glucosa;

    @BeforeEach
    void setUpLaboratory() {
        Laboratory lab = new Laboratory();
        lab.setName("Laboratorio de Prueba");
        // Tercera edad desde los 60 con 25 %.
        lab.setThirdAgeMinYears(60);
        lab.setThirdAgeDiscountPercent(new BigDecimal("25.00"));
        laboratory = laboratoryRepository.save(lab);
        TenantContext.setLaboratoryId(laboratory.getId());

        Customer c = new Customer();
        c.setName("Ana López");
        c.setAgeInDays(30 * 365);
        customer = customerRepository.save(c);

        hemograma = newTest("Hemograma", "200.00");
        glucosa = newTest("Glucosa", "100.00");
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    marroquinsoftware.labflowapi.model.Test newTest(String name, String price) {
        marroquinsoftware.labflowapi.model.Test test = new marroquinsoftware.labflowapi.model.Test();
        test.setName(name);
        test.setPrice(new BigDecimal(price));
        return testRepository.save(test);
    }

    void changePrice(marroquinsoftware.labflowapi.model.Test test, String price) {
        test.setPrice(new BigDecimal(price));
        testRepository.save(test);
    }

    QuoteRequest registered(Long... testIds) {
        QuoteRequest request = new QuoteRequest();
        request.setCustomerId(customer.getId());
        request.setTestIds(List.of(testIds));
        return request;
    }

    QuoteRequest unregistered(String name, int years, Long... testIds) {
        QuoteRequest request = new QuoteRequest();
        request.setPatientName(name);
        request.setPatientAgeInDays(years * 365);
        request.setTestIds(List.of(testIds));
        return request;
    }

    /** Hace que la cotización se haya emitido hace {@code daysAgo} días, conservando su vigencia en días. */
    void backdate(Long quoteId, int daysAgo) {
        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        quote.setQuotedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        quote.setValidUntil(quote.getQuotedAt().atZone(AppConstants.LAB_ZONE).toLocalDate()
                .plusDays(quote.getValidityDays()));
        quoteRepository.save(quote);
    }

    /**
     * Una cotización vencida ayer. Devuelve el DTO de la creación (su id y número no
     * cambian): releerla con el servicio fuera de una transacción no puede cargar
     * los exámenes.
     */
    QuoteDTO expiredQuote(QuoteRequest request) {
        QuoteDTO quote = quoteService.createQuote(request);
        backdate(quote.getId(), quote.getValidityDays() + 1);
        return quote;
    }

    LocalDate today() {
        return LocalDate.now(AppConstants.LAB_ZONE);
    }

    static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }
}
