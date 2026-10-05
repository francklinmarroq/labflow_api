package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.model.AccountingPeriodStatus;
import marroquinsoftware.labflowapi.model.InvoiceStatus;
import marroquinsoftware.labflowapi.model.JournalSourceType;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.PurchaseStatus;
import marroquinsoftware.labflowapi.model.SaleCondition;
import marroquinsoftware.labflowapi.repositories.AccountingPeriodRepository;
import marroquinsoftware.labflowapi.repositories.BillingSpecifications;
import marroquinsoftware.labflowapi.repositories.ExpenseRepository;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.InvoiceOrderRepository;
import marroquinsoftware.labflowapi.repositories.InvoiceRepository;
import marroquinsoftware.labflowapi.repositories.JournalEntryRepository;
import marroquinsoftware.labflowapi.repositories.JournalLineRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderSpecifications;
import marroquinsoftware.labflowapi.repositories.PurchaseRepository;
import marroquinsoftware.labflowapi.repositories.PurchaseSpecifications;
import marroquinsoftware.labflowapi.repositories.SupplierPaymentRepository;
import marroquinsoftware.labflowapi.repositories.SupplierRepository;
import marroquinsoftware.labflowapi.repositories.ReferringPhysicianRepository;
import marroquinsoftware.labflowapi.repositories.TestMethodRepository;
import marroquinsoftware.labflowapi.repositories.TestRunRepository;
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
import java.util.List;

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
    @Autowired InvoiceOrderRepository invoiceOrderRepository;
    @Autowired LabTestRepository labTestRepository;
    @Autowired JournalEntryRepository journalEntryRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired TestRunRepository testRunRepository;
    @Autowired LabOrderRepository labOrderRepository;
    @Autowired ReferringPhysicianRepository referringPhysicianRepository;
    @Autowired TestMethodRepository testMethodRepository;
    @Autowired JournalLineRepository journalLineRepository;
    @Autowired AccountingPeriodRepository accountingPeriodRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseRepository purchaseRepository;
    @Autowired SupplierPaymentRepository supplierPaymentRepository;

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
                BillingSpecifications.invoices(null, null, null, null, null, null, null), PageRequest.of(0, 50)));
    }

    @Test
    void listsInvoicesWithEveryFilterCombination() {
        assertDoesNotThrow(() -> {
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    InvoiceStatus.PENDIENTE, null, null, null, null, null, null), PageRequest.of(0, 50));
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    null, 1L, null, null, null, null, null), PageRequest.of(0, 50));
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    null, null, Instant.now().minusSeconds(3600), Instant.now(), null, null, null),
                    PageRequest.of(0, 50));
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    null, null, null, null, "juan", null, null), PageRequest.of(0, 50));
            // Filtro por etiqueta de la orden: agrega un join a lab_orders y a la
            // tabla de unión, tanto en la página como en la consulta de conteo.
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    null, null, null, null, null, 1L, null), PageRequest.of(0, 50));
            // Filtro por cliente de facturación: comparación sobre la llave foránea,
            // sin join, solo y combinado con todo lo demás.
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    null, null, null, null, null, null, 1L), PageRequest.of(0, 50));
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    InvoiceStatus.PENDIENTE, null, null, null, null, null, 1L), PageRequest.of(0, 50));
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    InvoiceStatus.PAGADA, 1L, Instant.now().minusSeconds(3600), Instant.now(), "000-001", 1L, 1L),
                    PageRequest.of(0, 50));
        });
    }

    // El listado de Órdenes: mismos filtros opcionales armados con Criteria, con y
    // sin etiqueta. Los fetch join del paciente y del médico solicitante (los dos
    // to-one, los dos LEFT) conviven con el join de etiquetas, y la consulta de
    // conteo se los salta.
    @Test
    void listsOrdersWithAndWithoutFilters() {
        assertDoesNotThrow(() -> {
            labOrderRepository.findAll(
                    LabOrderSpecifications.orders(null, null), PageRequest.of(0, 50));
            labOrderRepository.findAll(
                    LabOrderSpecifications.orders(OrderStatus.CANCELLED, null), PageRequest.of(0, 50));
            labOrderRepository.findAll(
                    LabOrderSpecifications.orders(null, 1L), PageRequest.of(0, 50));
            labOrderRepository.findAll(
                    LabOrderSpecifications.orders(OrderStatus.PENDING, 1L), PageRequest.of(0, 50));
        });
    }

    // El conteo de uso del catálogo de médicos solicitantes: un group by sobre el
    // join de LabOrder con el médico, devolviendo tuplas Object[]. Es JPQL, así que
    // el laboratorio lo agrega Hibernate por @TenantId; lo que se comprueba acá es
    // que Postgres acepta la proyección y el agrupamiento tal como los genera.
    @Test
    void countsOrdersPerReferringPhysician() {
        assertDoesNotThrow(() -> {
            referringPhysicianRepository.countUsageByPhysician();
            referringPhysicianRepository.findAllByOrderByNameAsc();
            referringPhysicianRepository.findByNormalizedName("dra. ana funez");
        });
    }

    // Los métodos del perfil de un examen: la lectura que hace la pantalla de órdenes
    // al ofrecerlos, y el conteo de exámenes que indican uno, que es lo que decide si
    // se puede quitar del perfil. El conteo navega la asociación LabTest.method, que
    // acá es una llave foránea nueva; H2 acepta el JPQL sin chistar, esta prueba es la
    // que dice si Postgres también.
    @Test
    void readsTheMethodsOfATemplateAndCountsTheExamsUsingOne() {
        assertDoesNotThrow(() -> {
            testMethodRepository.findByTestConfig_IdOrderByNameAsc(1L);
            testMethodRepository.findByTestConfig_IdAndNormalizedName(1L, "elisa");
            testMethodRepository.countLabTestsUsing(1L);
        });
    }

    // El bloqueo de exámenes de una página de órdenes: un `in` de ids más la
    // comparación contra el enum de estado. H2 acepta ese par sin chistar; esta
    // prueba es la que dice si Postgres también.
    @Test
    void resolvesTheTestsLockForAPageOfOrders() {
        assertDoesNotThrow(() -> {
            invoiceOrderRepository.findOrderIdsWithLiveInvoice(List.of(1L));
            invoiceOrderRepository.findOrderIdsWithLiveInvoice(List.of(1L, 2L, 3L));
        });
    }

    // Los estados financieros: agregados por cuenta con un `not in` sobre el enum
    // de origen (estado de resultados y cierre), sin límite inferior (balance), y
    // por origen sobre un `in` de cuentas (flujo de efectivo).
    @Test
    void aggregatesTheFinancialStatements() {
        LocalDate from = LocalDate.of(2025, 9, 1);
        LocalDate to = LocalDate.of(2025, 9, 30);
        assertDoesNotThrow(() -> {
            journalLineRepository.totalsByAccountExcluding(from, to,
                    List.of(JournalSourceType.CIERRE, JournalSourceType.ANULACION_CIERRE));
            journalLineRepository.totalsByAccountUpTo(to);
            journalLineRepository.netBeforeForAccounts(List.of(1L, 2L), from);
            journalLineRepository.totalsBySourceType(List.of(1L, 2L), from, to);
        });
    }

    // Los períodos contables: el bloqueo de partidas por fecha, el traslape al
    // cerrar y el último cerrado, que es el único que se puede reabrir.
    @Test
    void readsTheAccountingPeriods() {
        LocalDate date = LocalDate.of(2025, 9, 15);
        assertDoesNotThrow(() -> {
            accountingPeriodRepository.findContaining(AccountingPeriodStatus.CLOSED, date);
            accountingPeriodRepository.findOverlapping(AccountingPeriodStatus.CLOSED, date, date.plusDays(30));
            accountingPeriodRepository.findFirstByStatusOrderByEndDateDesc(AccountingPeriodStatus.CLOSED);
            accountingPeriodRepository.findAllByOrderByEndDateDescIdDesc();
        });
    }

    // Compras: la búsqueda de proveedores (lower + coalesce sobre un RTN que puede
    // ser nulo), el listado con fetch join del proveedor y sus filtros, y las
    // consultas derivadas con @EntityGraph de los reportes y los pagos.
    @Test
    void readsSuppliersPurchasesAndPayables() {
        LocalDate from = LocalDate.of(2025, 9, 1);
        LocalDate to = LocalDate.of(2025, 9, 30);
        assertDoesNotThrow(() -> {
            supplierRepository.findAll(PurchaseSpecifications.suppliers(null, null), PageRequest.of(0, 50));
            supplierRepository.findAll(PurchaseSpecifications.suppliers("médica", true), PageRequest.of(0, 50));
            supplierRepository.findFirstByRtn("08019999000011");
            purchaseRepository.findAll(PurchaseSpecifications.purchases(null, null, null, null, null),
                    PageRequest.of(0, 50));
            purchaseRepository.findAll(PurchaseSpecifications.purchases(from, to, 1L, SaleCondition.CREDITO,
                    PurchaseStatus.PENDIENTE), PageRequest.of(0, 50));
            purchaseRepository.findWithLockById(1L);
            purchaseRepository.findByAnnulledFalseAndPurchaseDateBetweenOrderByPurchaseDateAscIdAsc(from, to);
            purchaseRepository.findBySupplierIdAndConditionAndAnnulledFalseAndPurchaseDateLessThanEqualOrderByPurchaseDateAscIdAsc(
                    1L, SaleCondition.CREDITO, to);
            purchaseRepository.findByConditionAndAnnulledFalseAndPurchaseDateLessThanEqual(SaleCondition.CREDITO, to);
            supplierPaymentRepository.findByPurchaseIdOrderByPaymentNumberAsc(1L);
            supplierPaymentRepository.existsByPurchaseIdAndAnnulledFalse(1L);
            supplierPaymentRepository.findByPurchaseSupplierIdAndAnnulledFalseAndPaymentDateLessThanEqualOrderByPaymentDateAscPaymentNumberAsc(
                    1L, to);
            supplierPaymentRepository.findByAnnulledFalseAndPaymentDateLessThanEqual(to);
        });
    }

    // Facturas de varias órdenes: la orden de una factura vive en invoice_orders, así
    // que los filtros por orden y por etiqueta son subconsultas exists; las órdenes
    // pendientes de facturar son un not exists con isNotEmpty sobre los exámenes.
    @Test
    void readsInvoicesThroughTheirOrders() {
        assertDoesNotThrow(() -> {
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    null, 1L, null, null, null, null, null), PageRequest.of(0, 50));
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    null, null, null, null, null, 1L, null), PageRequest.of(0, 50));
            invoiceRepository.findAll(BillingSpecifications.invoices(
                    InvoiceStatus.PENDIENTE, 1L, Instant.now().minusSeconds(3600), Instant.now(), "000", 1L, 1L),
                    PageRequest.of(0, 50));
            invoiceOrderRepository.findLiveInvoiceOfOrder(1L);
            invoiceOrderRepository.countByInvoiceId(1L);
            labOrderRepository.findAll(LabOrderSpecifications.uninvoiced(null, null, null), PageRequest.of(0, 50));
            labOrderRepository.findAll(LabOrderSpecifications.uninvoiced(1L,
                    Instant.now().minusSeconds(86400), Instant.now()), PageRequest.of(0, 50));
            invoiceRepository.salesItemRows(Instant.now().minusSeconds(86400), Instant.now());
        });
    }

    // Registro de ventas detallado: proyecciones de cabeceras, líneas (con el enum
    // y la cantidad numeric), órdenes por factura y unidades por orden con una
    // subconsulta in sobre invoice_orders.
    @Test
    void readsTheSalesRegister() {
        Instant from = Instant.now().minusSeconds(86400);
        Instant to = Instant.now();
        assertDoesNotThrow(() -> {
            invoiceRepository.registerInvoiceRows(from, to);
            invoiceRepository.registerItemRows(from, to);
            invoiceOrderRepository.registerOrderRows(from, to);
            labTestRepository.invoicedOrderUnits(from, to);
        });
    }

    @Test
    void listsReceivables() {
        assertDoesNotThrow(() -> {
            invoiceRepository.findReceivables(PageRequest.of(0, 50));
            invoiceRepository.totalReceivable();
        });
    }

    // El saldo por cliente de facturación: un group by con join y agregados sobre
    // numeric. H2 lo acepta sin más; esta prueba es la que dice si Postgres también.
    @Test
    void aggregatesReceivablesByBillingClient() {
        assertDoesNotThrow(() -> invoiceRepository.receivablesByBillingClient());
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

    // El detalle de orden trae todas las corridas de la orden en una sola consulta
    // con fetch joins (examen + resultados + parámetro). Valida que el JPQL con
    // DISTINCT + JOIN FETCH de colección compile y ejecute contra Postgres real.
    @Test
    void loadsAllRunsOfAnOrderWithFetchJoins() {
        assertDoesNotThrow(() -> testRunRepository.findByOrderIdWithResults(1L));
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
