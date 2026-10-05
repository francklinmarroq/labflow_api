package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.*;
import marroquinsoftware.labflowapi.repositories.AccountRepository;
import marroquinsoftware.labflowapi.repositories.JournalEntryRepository;
import marroquinsoftware.labflowapi.repositories.PurchaseRepository;
import marroquinsoftware.labflowapi.repositories.SupplierPaymentRepository;
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
 * Compras a proveedores: el catálogo de proveedores, el documento de compra con
 * su desglose fiscal y su asiento, los pagos a crédito, el bloqueo por período
 * cerrado, los tres reportes de cuentas por pagar y las filas propias de compras
 * y pagos en el flujo de efectivo.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({PurchaseServiceImp.class, SupplierPaymentServiceImp.class, SupplierServiceImp.class,
        PurchaseReportServiceImp.class, JournalServiceImp.class, AccountingPeriodServiceImp.class,
        AccountingReportServiceImp.class, AccountSeeder.class, TenantIdentifierResolver.class,
        PurchaseTest.JacksonForTest.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class PurchaseTest {

    static class JacksonForTest {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
    }

    @Autowired PurchaseService purchaseService;
    @Autowired SupplierPaymentService paymentService;
    @Autowired SupplierService supplierService;
    @Autowired PurchaseReportService reportService;
    @Autowired AccountingPeriodService periodService;
    @Autowired AccountingReportService accountingReportService;
    @Autowired JournalService journalService;
    @Autowired AccountSeeder accountSeeder;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository journalEntryRepository;
    @Autowired PurchaseRepository purchaseRepository;
    @Autowired SupplierPaymentRepository supplierPaymentRepository;

    private static final LocalDate SEP_1 = LocalDate.of(2025, 9, 1);
    private static final LocalDate SEP_10 = LocalDate.of(2025, 9, 10);
    private static final LocalDate SEP_20 = LocalDate.of(2025, 9, 20);
    private static final LocalDate SEP_30 = LocalDate.of(2025, 9, 30);
    private static final LocalDate OCT_15 = LocalDate.of(2025, 10, 15);

    private SupplierDTO supplier;

    @BeforeEach
    void setUp() {
        TenantContext.setLaboratoryId(1L);
        accountSeeder.seedDefaultAccounts();
        supplier = supplierService.createSupplier(
                new SupplierDTO(null, "Distribuidora Médica", "08019999000011", null, null, null, true));
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    private Long accountId(String code) { return accountRepository.findByCode(code).orElseThrow().getId(); }

    private static BigDecimal amount(String value) { return new BigDecimal(value); }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "se esperaba " + expected + " y fue " + actual);
    }

    private PurchaseLineRequest line(String price, IsvRate rate) {
        return new PurchaseLineRequest("Reactivos", BigDecimal.ONE, amount(price), rate, accountId("5104"));
    }

    private PurchaseRequest request(LocalDate date, SaleCondition condition, PurchaseLineRequest... lines) {
        return new PurchaseRequest(supplier.getId(), date, "000-001-01-00000123", null, null, condition,
                condition == SaleCondition.CONTADO ? PaymentMethod.EFECTIVO : null, null,
                new ArrayList<>(List.of(lines)));
    }

    private PurchaseDTO credit(LocalDate date, String exemptAmount) {
        return purchaseService.registerPurchase(request(date, SaleCondition.CREDITO, line(exemptAmount, IsvRate.EXENTO)));
    }

    private SupplierPaymentRequest payment(LocalDate date, String value) {
        return new SupplierPaymentRequest(date, amount(value), PaymentMethod.TRANSFERENCIA, null);
    }

    /** Debe - haber de una cuenta del sistema en una partida. */
    private BigDecimal net(JournalEntry entry, SystemAccountKey key) {
        Long id = journalService.systemAccount(key).getId();
        return entry.getLines().stream().filter(l -> l.getAccount().getId().equals(id))
                .map(l -> l.getDebit().subtract(l.getCredit())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private JournalEntry entryOf(JournalSourceType type, Long sourceId) {
        return journalEntryRepository.findFirstBySourceTypeAndSourceId(type, sourceId).orElseThrow();
    }

    // --- Proveedores -----------------------------------------------------------

    @Test
    void duplicateRtnIsRejectedNamingTheOwner() {
        APIException ex = assertThrows(APIException.class, () -> supplierService.createSupplier(
                new SupplierDTO(null, "Otro", "08019999000011", null, null, null, true)));
        assertTrue(ex.getMessage().contains("Distribuidora Médica"), ex.getMessage());
    }

    @Test
    void duplicateRtnOfADeactivatedSupplierSuggestsReactivating() {
        supplierService.setActive(supplier.getId(), false);

        APIException ex = assertThrows(APIException.class, () -> supplierService.createSupplier(
                new SupplierDTO(null, "Otro", "08019999000011", null, null, null, true)));
        assertTrue(ex.getMessage().contains("reactívelo"), ex.getMessage());
    }

    @Test
    void editingIntoAnotherSuppliersRtnIsRejected() {
        SupplierDTO other = supplierService.createSupplier(
                new SupplierDTO(null, "Laboratorios Unidos", "08011111222233", null, null, null, true));
        other.setRtn("08019999000011");

        assertThrows(APIException.class, () -> supplierService.updateSupplier(other.getId(), other));
    }

    @Test
    void suppliersWithoutRtnDoNotCollide() {
        supplierService.createSupplier(new SupplierDTO(null, "Ferretería", null, null, null, null, true));
        assertDoesNotThrow(() -> supplierService.createSupplier(
                new SupplierDTO(null, "Librería", "  ", null, null, null, true)));
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(APIException.class, () -> supplierService.createSupplier(
                new SupplierDTO(null, "   ", null, null, null, null, true)));
    }

    @Test
    void searchMatchesNameOrRtnAndFiltersByState() {
        supplierService.createSupplier(new SupplierDTO(null, "Ferretería Central", "08017777", null, null, null, true));

        assertEquals(1, supplierService.getSuppliers(0, 50, "name", "asc", "médica", null).getContent().size());
        assertEquals(1, supplierService.getSuppliers(0, 50, "name", "asc", "7777", null).getContent().size());
        supplierService.setActive(supplier.getId(), false);
        assertEquals(1, supplierService.getSuppliers(0, 50, "name", "asc", null, true).getContent().size());
    }

    @Test
    void deactivatedSupplierCannotReceiveNewPurchases() {
        supplierService.setActive(supplier.getId(), false);

        assertThrows(APIException.class, () -> credit(SEP_10, "100.00"));
    }

    // --- Compras ---------------------------------------------------------------

    @Test
    void mixedLinesProduceTheFiscalBreakdown() {
        PurchaseDTO dto = purchaseService.registerPurchase(request(SEP_10, SaleCondition.CREDITO,
                line("100.00", IsvRate.EXENTO),
                new PurchaseLineRequest("Guantes", amount("3"), amount("333.33"), IsvRate.GRAVADO_15, accountId("5104")),
                line("200.00", IsvRate.GRAVADO_18)));

        assertAmount("100.00", dto.getExemptBase());
        assertAmount("999.99", dto.getTaxedBase15());
        assertAmount("200.00", dto.getTaxedBase18());
        assertAmount("150.00", dto.getIsv15());
        assertAmount("36.00", dto.getIsv18());
        assertAmount("1485.99", dto.getTotal());
        assertEquals(3, dto.getLines().size());
    }

    @Test
    void cashPurchaseBooksLinesIsvAndCash() {
        PurchaseDTO dto = purchaseService.registerPurchase(
                request(SEP_10, SaleCondition.CONTADO, line("1000.00", IsvRate.GRAVADO_15)));

        assertEquals(PurchaseStatus.PAGADA, dto.getStatus());
        JournalEntry entry = entryOf(JournalSourceType.COMPRA, dto.getId());
        assertEquals(SEP_10, entry.getEntryDate(), "el asiento va con la fecha de la compra");
        assertAmount("150.00", net(entry, SystemAccountKey.ISV_NO_RECUPERABLE_COMPRAS));
        assertAmount("-1150.00", net(entry, SystemAccountKey.CAJA));
        assertEquals(dto.getJournalEntryId(), entry.getId());
    }

    @Test
    void creditPurchaseBooksAgainstPayablesAndStaysPending() {
        PurchaseDTO dto = credit(SEP_10, "5000.00");

        assertEquals(PurchaseStatus.PENDIENTE, dto.getStatus());
        assertAmount("5000.00", dto.getBalance());
        JournalEntry entry = entryOf(JournalSourceType.COMPRA, dto.getId());
        assertAmount("-5000.00", net(entry, SystemAccountKey.CUENTAS_POR_PAGAR));
        assertAmount("0", net(entry, SystemAccountKey.ISV_NO_RECUPERABLE_COMPRAS));
    }

    @Test
    void isvAccountIsCreatedForLabsSeededBeforeIt() {
        accountRepository.findBySystemKey(SystemAccountKey.ISV_NO_RECUPERABLE_COMPRAS)
                .ifPresent(accountRepository::delete);

        purchaseService.registerPurchase(request(SEP_10, SaleCondition.CONTADO, line("100.00", IsvRate.GRAVADO_15)));

        Account isv = accountRepository.findBySystemKey(SystemAccountKey.ISV_NO_RECUPERABLE_COMPRAS).orElseThrow();
        assertEquals("5107", isv.getCode());
        assertEquals(AccountType.GASTO, isv.getType());
    }

    @Test
    void purchaseWithoutLinesIsRejected() {
        APIException ex = assertThrows(APIException.class,
                () -> purchaseService.registerPurchase(request(SEP_10, SaleCondition.CREDITO)));
        assertTrue(ex.getMessage().contains("al menos una línea"), ex.getMessage());
    }

    @Test
    void invalidLineAccountsAreRejected() {
        Account inactive = accountRepository.findByCode("5103").orElseThrow();
        inactive.setActive(false);
        accountRepository.save(inactive);

        for (String code : List.of("5103", "1101", "4101")) {   // inactiva, de sistema, de ingresos
            PurchaseLineRequest bad = new PurchaseLineRequest("X", BigDecimal.ONE, amount("10"),
                    IsvRate.EXENTO, accountId(code));
            APIException ex = assertThrows(APIException.class,
                    () -> purchaseService.registerPurchase(request(SEP_10, SaleCondition.CREDITO, bad)),
                    "la cuenta " + code + " no debe aceptarse");
            assertTrue(ex.getMessage().contains("línea 1"), ex.getMessage());
        }
    }

    @Test
    void assetAccountsAreAccepted() {
        PurchaseLineRequest equipment = new PurchaseLineRequest("Centrífuga", BigDecimal.ONE, amount("8000"),
                IsvRate.GRAVADO_15, accountId("1201"));
        assertDoesNotThrow(() -> purchaseService.registerPurchase(request(SEP_10, SaleCondition.CREDITO, equipment)));
    }

    @Test
    void nonPositiveQuantityOrPriceIsRejected() {
        PurchaseLineRequest zero = new PurchaseLineRequest("X", BigDecimal.ZERO, amount("10"),
                IsvRate.EXENTO, accountId("5104"));
        assertThrows(APIException.class,
                () -> purchaseService.registerPurchase(request(SEP_10, SaleCondition.CREDITO, zero)));
    }

    @Test
    void annullingACashPurchaseReversesItsEntry() {
        PurchaseDTO dto = purchaseService.registerPurchase(
                request(SEP_10, SaleCondition.CONTADO, line("300.00", IsvRate.EXENTO)));

        PurchaseDTO annulled = purchaseService.annulPurchase(dto.getId(), "Duplicada");

        assertEquals(PurchaseStatus.ANULADA, annulled.getStatus());
        JournalEntry reversal = entryOf(JournalSourceType.ANULACION_COMPRA, dto.getId());
        assertAmount("300.00", net(reversal, SystemAccountKey.CAJA));
        assertThrows(APIException.class, () -> purchaseService.annulPurchase(dto.getId(), "otra vez"));
    }

    // --- Pagos -----------------------------------------------------------------

    @Test
    void paymentsMoveTheBalanceAndStatus() {
        PurchaseDTO dto = credit(SEP_10, "5000.00");

        PurchaseDTO partial = paymentService.registerPayment(dto.getId(), payment(SEP_20, "2000.00"));
        assertEquals(PurchaseStatus.PARCIAL, partial.getStatus());
        assertAmount("3000.00", partial.getBalance());
        assertEquals(1L, partial.getPayments().get(0).getPaymentNumber());
        JournalEntry entry = entryOf(JournalSourceType.PAGO_PROVEEDOR, partial.getPayments().get(0).getId());
        assertEquals(SEP_20, entry.getEntryDate());
        assertAmount("2000.00", net(entry, SystemAccountKey.CUENTAS_POR_PAGAR));
        assertAmount("-2000.00", net(entry, SystemAccountKey.BANCOS));

        PurchaseDTO paid = paymentService.registerPayment(dto.getId(), payment(SEP_30, "3000.00"));
        assertEquals(PurchaseStatus.PAGADA, paid.getStatus());
        assertAmount("0", paid.getBalance());
        assertEquals(2L, paid.getPayments().get(1).getPaymentNumber(), "el recibo es correlativo");
    }

    @Test
    void paymentAboveTheBalanceIsRejected() {
        PurchaseDTO dto = credit(SEP_10, "500.00");

        APIException ex = assertThrows(APIException.class,
                () -> paymentService.registerPayment(dto.getId(), payment(SEP_20, "500.01")));
        assertTrue(ex.getMessage().contains("excede"), ex.getMessage());
    }

    @Test
    void paymentAgainstACashPurchaseIsRejected() {
        PurchaseDTO dto = purchaseService.registerPurchase(
                request(SEP_10, SaleCondition.CONTADO, line("300.00", IsvRate.EXENTO)));

        assertThrows(APIException.class, () -> paymentService.registerPayment(dto.getId(), payment(SEP_20, "10")));
    }

    @Test
    void annullingAPaymentRestoresTheBalanceAndUnblocksTheAnnulment() {
        PurchaseDTO dto = credit(SEP_10, "5000.00");
        paymentService.registerPayment(dto.getId(), payment(SEP_20, "3000.00"));
        PurchaseDTO paid = paymentService.registerPayment(dto.getId(), payment(SEP_20, "2000.00"));

        APIException blocked = assertThrows(APIException.class,
                () -> purchaseService.annulPurchase(dto.getId(), "Error"));
        assertTrue(blocked.getMessage().contains("pagos vigentes"), blocked.getMessage());

        Long secondPayment = paid.getPayments().get(1).getId();
        PurchaseDTO afterAnnul = paymentService.annulPayment(secondPayment, "Transferencia rechazada");
        assertEquals(PurchaseStatus.PARCIAL, afterAnnul.getStatus());
        assertAmount("2000.00", afterAnnul.getBalance());
        assertTrue(journalEntryRepository.findFirstBySourceTypeAndSourceId(
                JournalSourceType.ANULACION_PAGO_PROVEEDOR, secondPayment).isPresent());

        paymentService.annulPayment(paid.getPayments().get(0).getId(), "Error");
        assertEquals(PurchaseStatus.ANULADA, purchaseService.annulPurchase(dto.getId(), "Error").getStatus());
    }

    // --- Bloqueo por período cerrado --------------------------------------------

    @Test
    void purchasesDatedInAClosedPeriodAreRejectedWithoutLeavingAnything() {
        periodService.close(SEP_1, SEP_30);
        long purchasesBefore = purchaseRepository.count();
        long entriesBefore = journalEntryRepository.count();

        for (SaleCondition condition : SaleCondition.values()) {
            APIException ex = assertThrows(APIException.class, () -> purchaseService.registerPurchase(
                    request(SEP_10, condition, line("100.00", IsvRate.EXENTO))));
            assertTrue(ex.getMessage().contains("01/09/2025") && ex.getMessage().contains("está cerrado"),
                    ex.getMessage());
        }
        assertEquals(purchasesBefore, purchaseRepository.count(), "no debe quedar el documento");
        assertEquals(entriesBefore, journalEntryRepository.count(), "no debe quedar el asiento");
    }

    @Test
    void paymentsDatedInAClosedPeriodAreRejectedWithoutTouchingTheBalance() {
        PurchaseDTO dto = credit(LocalDate.of(2025, 8, 20), "1000.00");
        periodService.close(SEP_1, SEP_30);

        APIException ex = assertThrows(APIException.class,
                () -> paymentService.registerPayment(dto.getId(), payment(SEP_20, "400.00")));
        assertTrue(ex.getMessage().contains("está cerrado"), ex.getMessage());
        assertEquals(0, supplierPaymentRepository.count());
        assertAmount("1000.00", purchaseService.getPurchase(dto.getId()).getBalance());
    }

    // --- Reportes ----------------------------------------------------------------

    @Test
    void purchaseBookListsOnlyLivePurchasesWithTotals() {
        purchaseService.registerPurchase(request(SEP_10, SaleCondition.CONTADO, line("1000.00", IsvRate.GRAVADO_15)));
        credit(SEP_20, "500.00");
        PurchaseDTO annulled = credit(SEP_20, "999.00");
        purchaseService.annulPurchase(annulled.getId(), "Error");
        credit(OCT_15, "77.00");   // fuera del rango

        PurchaseBookDTO book = reportService.getPurchaseBook(SEP_1, SEP_30);

        assertEquals(2, book.getRows().size());
        assertAmount("500.00", book.getTotalExemptBase());
        assertAmount("1000.00", book.getTotalTaxedBase15());
        assertAmount("150.00", book.getTotalIsv15());
        assertAmount("1650.00", book.getTotal());
        assertEquals("08019999000011", book.getRows().get(0).getSupplierRtn());
    }

    @Test
    void supplierStatementRunsTheBalanceInOrder() {
        credit(LocalDate.of(2025, 8, 5), "300.00");      // antes del rango: saldo inicial
        credit(SEP_10, "1000.00");
        PurchaseDTO second = credit(SEP_20, "500.00");
        paymentService.registerPayment(second.getId(), payment(SEP_30, "200.00"));
        purchaseService.registerPurchase(request(SEP_10, SaleCondition.CONTADO, line("50.00", IsvRate.EXENTO)));

        SupplierStatementDTO statement = reportService.getSupplierStatement(supplier.getId(), SEP_1, SEP_30);

        assertAmount("300.00", statement.getOpeningBalance());
        assertEquals(List.of("COMPRA", "COMPRA", "PAGO"),
                statement.getRows().stream().map(SupplierStatementRowDTO::getType).toList(),
                "las de contado no generan deuda y no aparecen");
        assertAmount("1500.00", statement.getTotalCharges());
        assertAmount("200.00", statement.getTotalPayments());
        assertAmount("1600.00", statement.getClosingBalance());
        assertAmount("1600.00", statement.getRows().get(2).getBalance());
    }

    @Test
    void payablesAgingBucketsAddUpToEachSuppliersBalance() {
        LocalDate cutoff = LocalDate.of(2025, 12, 31);
        credit(LocalDate.of(2025, 12, 20), "100.00");   // 11 días
        credit(LocalDate.of(2025, 11, 15), "200.00");   // 46 días
        credit(LocalDate.of(2025, 10, 10), "300.00");   // 82 días
        PurchaseDTO old = credit(LocalDate.of(2025, 8, 1), "400.00");   // 152 días
        paymentService.registerPayment(old.getId(), payment(LocalDate.of(2025, 8, 15), "150.00"));
        PurchaseDTO settled = credit(LocalDate.of(2025, 12, 1), "50.00");
        paymentService.registerPayment(settled.getId(), payment(LocalDate.of(2025, 12, 2), "50.00"));

        PayablesAgingDTO aging = reportService.getPayablesAging(cutoff);

        assertEquals(1, aging.getRows().size());
        PayablesAgingRowDTO row = aging.getRows().get(0);
        assertAmount("100.00", row.getDays0To30());
        assertAmount("200.00", row.getDays31To60());
        assertAmount("300.00", row.getDays61To90());
        assertAmount("250.00", row.getOver90());
        assertAmount("850.00", row.getTotal());
        assertEquals(0, row.getTotal().compareTo(row.getDays0To30().add(row.getDays31To60())
                .add(row.getDays61To90()).add(row.getOver90())));
    }

    // --- Flujo de efectivo ---------------------------------------------------------

    @Test
    void cashFlowShowsPurchasesAndSupplierPaymentsInTheirOwnRows() {
        PurchaseDTO cash = purchaseService.registerPurchase(
                request(SEP_10, SaleCondition.CONTADO, line("1000.00", IsvRate.GRAVADO_15)));
        PurchaseDTO onCredit = credit(SEP_10, "800.00");
        paymentService.registerPayment(onCredit.getId(), payment(SEP_20, "300.00"));

        CashFlowDTO flow = accountingReportService.getCashFlow(SEP_1, SEP_30);

        assertAmount("1150.00", outflow(flow, CashFlowCategory.COMPRAS));
        assertAmount("300.00", outflow(flow, CashFlowCategory.PAGOS_PROVEEDORES));
        assertTrue(flow.getOutflows().stream().noneMatch(r -> r.getCategory() == CashFlowCategory.GASTOS));
        assertAmount("-1450.00", flow.getClosingBalance());

        BalanceSheetDTO sheet = accountingReportService.getBalanceSheet(SEP_30);
        BigDecimal cashAndBanks = sheet.getAssetLines().stream()
                .filter(l -> l.getCode().equals("1101") || l.getCode().equals("1102"))
                .map(FinancialStatementLineDTO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, flow.getClosingBalance().compareTo(cashAndBanks), "debe cuadrar contra Caja + Bancos");
        assertTrue(sheet.isBalanced());
        assertNotNull(cash.getId());
    }

    private BigDecimal outflow(CashFlowDTO flow, CashFlowCategory category) {
        return flow.getOutflows().stream().filter(r -> r.getCategory() == category)
                .map(CashFlowRowDTO::getAmount).findFirst().orElse(BigDecimal.ZERO);
    }
}
