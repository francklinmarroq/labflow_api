package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.model.InvoiceStatus;
import marroquinsoftware.labflowapi.model.JournalSourceType;
import marroquinsoftware.labflowapi.repositories.BillingSpecifications;
import marroquinsoftware.labflowapi.repositories.ExpenseRepository;
import marroquinsoftware.labflowapi.repositories.InvoiceRepository;
import marroquinsoftware.labflowapi.repositories.JournalEntryRepository;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import marroquinsoftware.labflowapi.tenant.TenantIdentifierResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ejecuta contra PostgreSQL real los listados con filtros opcionales.
 *
 * <p>Existe porque H2 (el resto de la suite) es mucho más laxo con los tipos y
 * deja pasar consultas que en producción fallan: un parámetro que solo aparece
 * dentro de {@code concat} no tiene tipo inferible para Postgres, que lo trata
 * como bytea y responde "function lower(bytea) does not exist". Así se cayó el
 * listado de facturas la primera vez que se abrió la pantalla.
 *
 * <p>Necesita un Postgres en localhost:55432; sin él los tests se saltan, para
 * no romper el build de quien no lo tenga levantado:
 *
 * <pre>
 * docker run -d --rm --name labflow-pg-test -e POSTGRES_PASSWORD=test \
 *   -e POSTGRES_DB=labflow -p 55432:5432 postgres:16-alpine
 * </pre>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TenantIdentifierResolver.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/labflow",
        "spring.datasource.username=postgres",
        "spring.datasource.password=test",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.sql.init.mode=never"
})
class PostgresQueryCompatibilityTest {

    private static final String URL = "jdbc:postgresql://localhost:55432/labflow";

    @Autowired InvoiceRepository invoiceRepository;
    @Autowired JournalEntryRepository journalEntryRepository;
    @Autowired ExpenseRepository expenseRepository;

    @BeforeAll
    static void requirePostgres() {
        boolean available;
        try (var ignored = DriverManager.getConnection(URL, "postgres", "test")) {
            available = true;
        } catch (Exception e) {
            available = false;
        }
        assumeTrue(available, "PostgreSQL de pruebas no disponible en localhost:55432; se saltan estos tests");
        TenantContext.setLaboratoryId(1L);
    }

    @AfterAll
    static void clearTenant() { TenantContext.clear(); }

    // Lo que hace la pantalla de Facturas al abrirse: todos los filtros vacíos.
    @Test
    void listsInvoicesWithoutFilters() {
        assertDoesNotThrow(() -> invoiceRepository.findAll(
                BillingSpecifications.invoices(null, null, null, null, null), PageRequest.of(0, 50)));
    }

    @Test
    void listsInvoicesWithEveryFilterCombination() {
        assertDoesNotThrow(() -> {
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    InvoiceStatus.PENDIENTE, null, null, null, null), PageRequest.of(0, 50));
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    null, 1L, null, null, null), PageRequest.of(0, 50));
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    null, null, Instant.now().minusSeconds(3600), Instant.now(), null), PageRequest.of(0, 50));
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    null, null, null, null, "juan"), PageRequest.of(0, 50));
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    InvoiceStatus.PAGADA, 1L, Instant.now().minusSeconds(3600), Instant.now(), "000-001"),
                    PageRequest.of(0, 50));
        });
    }

    @Test
    void listsReceivables() {
        assertDoesNotThrow(() -> {
            invoiceRepository.findReceivables(PageRequest.of(0, 50));
            invoiceRepository.totalReceivable();
        });
    }

    @Test
    void listsJournalEntriesWithAndWithoutFilters() {
        assertDoesNotThrow(() -> {
            journalEntryRepository.findAll(
                    BillingSpecifications.journalEntries(null, null, null), PageRequest.of(0, 50));
            journalEntryRepository.findAll(BillingSpecifications.journalEntries(
                    LocalDate.now().minusMonths(1), LocalDate.now(), JournalSourceType.FACTURA),
                    PageRequest.of(0, 50));
        });
    }

    @Test
    void listsExpensesWithAndWithoutFilters() {
        assertDoesNotThrow(() -> {
            expenseRepository.findAll(BillingSpecifications.expenses(null, null), PageRequest.of(0, 50));
            expenseRepository.findAll(BillingSpecifications.expenses(
                    LocalDate.now().minusMonths(1), LocalDate.now()), PageRequest.of(0, 50));
        });
    }
}
