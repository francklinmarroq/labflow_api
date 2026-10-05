package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.AccountingPeriodDTO;
import marroquinsoftware.labflowapi.payload.BalanceSheetDTO;
import marroquinsoftware.labflowapi.payload.CashFlowDTO;
import marroquinsoftware.labflowapi.payload.CashFlowRowDTO;
import marroquinsoftware.labflowapi.payload.ExpenseRequest;
import marroquinsoftware.labflowapi.payload.FinancialStatementLineDTO;
import marroquinsoftware.labflowapi.payload.IncomeStatementDTO;
import marroquinsoftware.labflowapi.repositories.AccountRepository;
import marroquinsoftware.labflowapi.repositories.JournalEntryRepository;
import marroquinsoftware.labflowapi.service.AccountSeeder;
import marroquinsoftware.labflowapi.service.AccountServiceImp;
import marroquinsoftware.labflowapi.service.AccountingPeriodService;
import marroquinsoftware.labflowapi.service.AccountingPeriodServiceImp;
import marroquinsoftware.labflowapi.service.AccountingReportService;
import marroquinsoftware.labflowapi.service.AccountingReportServiceImp;
import marroquinsoftware.labflowapi.service.ExpenseServiceImp;
import marroquinsoftware.labflowapi.service.JournalService;
import marroquinsoftware.labflowapi.service.JournalService.LinePlan;
import marroquinsoftware.labflowapi.service.JournalServiceImp;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cierre y reapertura de períodos contables, el bloqueo de partidas en un
 * período cerrado, y los tres estados financieros (resultados, balance general
 * y flujo de efectivo) con y sin cierres.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({AccountingPeriodServiceImp.class, AccountingReportServiceImp.class, JournalServiceImp.class,
        ExpenseServiceImp.class, AccountServiceImp.class, AccountSeeder.class,
        TenantIdentifierResolver.class, AccountingPeriodTest.JacksonForTest.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class AccountingPeriodTest {

    static class JacksonForTest {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
    }

    @Autowired AccountingPeriodService periodService;
    @Autowired AccountingReportService reportService;
    @Autowired JournalService journalService;
    @Autowired ExpenseServiceImp expenseService;
    @Autowired AccountServiceImp accountService;
    @Autowired AccountSeeder accountSeeder;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository journalEntryRepository;

    // Septiembre y octubre de 2025: fechas fijas en el pasado, para que nada del
    // test dependa del día en que corre.
    private static final LocalDate SEP_1 = LocalDate.of(2025, 9, 1);
    private static final LocalDate SEP_15 = LocalDate.of(2025, 9, 15);
    private static final LocalDate SEP_30 = LocalDate.of(2025, 9, 30);
    private static final LocalDate OCT_1 = LocalDate.of(2025, 10, 1);
    private static final LocalDate OCT_10 = LocalDate.of(2025, 10, 10);
    private static final LocalDate OCT_31 = LocalDate.of(2025, 10, 31);

    @BeforeEach
    void setUpTenant() {
        TenantContext.setLaboratoryId(1L);
        accountSeeder.seedDefaultAccounts();
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    private Account system(SystemAccountKey key) { return journalService.systemAccount(key); }

    private Account byCode(String code) {
        return accountRepository.findByCode(code).orElseThrow();
    }

    private BigDecimal amount(String value) { return new BigDecimal(value); }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertAmount(expected, actual, "");
    }

    private static void assertAmount(String expected, BigDecimal actual, String message) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                message + " (se esperaba " + expected + " y fue " + actual + ")");
    }

    /** Venta de contado: cargo a Caja, abono a ingresos por servicios. */
    private void sale(LocalDate date, String value) {
        journalService.post(date, "Venta", JournalSourceType.MANUAL, null, List.of(
                LinePlan.debit(system(SystemAccountKey.CAJA), amount(value)),
                LinePlan.credit(system(SystemAccountKey.INGRESOS_SERVICIOS), amount(value))));
    }

    /** Gasto pagado en efectivo a "Reactivos e insumos" (5104). */
    private void expense(LocalDate date, String value) {
        expenseService.createExpense(new ExpenseRequest(date, "Reactivos", amount(value),
                byCode("5104").getId(), PaymentMethod.EFECTIVO));
    }

    /** Saldo neto (debe - haber) de una cuenta en las líneas de una partida. */
    private BigDecimal netIn(JournalEntry entry, Account account) {
        return entry.getLines().stream()
                .filter(l -> l.getAccount().getId().equals(account.getId()))
                .map(l -> l.getDebit().subtract(l.getCredit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // --- Cierre ---------------------------------------------------------------

    @Test
    void closingWithProfitZeroesIncomeAndExpensesAndCreditsTheResult() {
        sale(SEP_15, "1000.00");
        expense(SEP_15, "300.00");

        AccountingPeriodDTO period = periodService.close(SEP_1, SEP_30);

        assertEquals(AccountingPeriodStatus.CLOSED, period.getStatus());
        assertAmount("700.00", period.getResultAmount());
        JournalEntry closing = journalEntryRepository.findById(period.getClosingEntryId()).orElseThrow();
        assertEquals(JournalSourceType.CIERRE, closing.getSourceType());
        assertEquals(SEP_30, closing.getEntryDate(), "la partida de cierre va con la fecha de fin del período");
        assertAmount("1000.00", netIn(closing, system(SystemAccountKey.INGRESOS_SERVICIOS)));
        assertAmount("-300.00", netIn(closing, byCode("5104")));
        assertAmount("-700.00", netIn(closing, system(SystemAccountKey.RESULTADO_DEL_EJERCICIO)));
    }

    @Test
    void closingWithLossDebitsTheResult() {
        sale(SEP_15, "200.00");
        expense(SEP_15, "500.00");

        AccountingPeriodDTO period = periodService.close(SEP_1, SEP_30);

        assertAmount("-300.00", period.getResultAmount());
        JournalEntry closing = journalEntryRepository.findById(period.getClosingEntryId()).orElseThrow();
        assertAmount("300.00", netIn(closing, system(SystemAccountKey.RESULTADO_DEL_EJERCICIO)));
    }

    @Test
    void closingWithoutMovementsCreatesNoEntry() {
        AccountingPeriodDTO period = periodService.close(SEP_1, SEP_30);

        assertEquals(AccountingPeriodStatus.CLOSED, period.getStatus());
        assertNull(period.getClosingEntryId(), "sin saldos no hay nada que trasladar");
        assertAmount("0.00", period.getResultAmount());
    }

    @Test
    void closingTransfersBalancesOfDeactivatedAccounts() {
        expense(SEP_15, "100.00");
        Account reactivos = byCode("5104");
        reactivos.setActive(false);
        accountRepository.save(reactivos);

        AccountingPeriodDTO period = periodService.close(SEP_1, SEP_30);

        assertAmount("-100.00", period.getResultAmount());
    }

    @Test
    void overlappingPeriodIsRejected() {
        periodService.close(SEP_1, SEP_30);

        APIException ex = assertThrows(APIException.class,
                () -> periodService.close(SEP_15, OCT_31));
        assertTrue(ex.getMessage().contains("01/09/2025"), ex.getMessage());
    }

    @Test
    void resultAccountIsCreatedForLabsSeededBeforeIt() {
        // Un laboratorio sembrado antes de existir la cuenta no la tiene.
        accountRepository.findBySystemKey(SystemAccountKey.RESULTADO_DEL_EJERCICIO)
                .ifPresent(accountRepository::delete);

        accountService.getAccounts(false);

        Account result = accountRepository.findBySystemKey(SystemAccountKey.RESULTADO_DEL_EJERCICIO).orElseThrow();
        assertEquals(AccountType.CAPITAL, result.getType());
        accountService.getAccounts(false);
        assertEquals(1, accountRepository.findAll().stream()
                .filter(a -> a.getSystemKey() == SystemAccountKey.RESULTADO_DEL_EJERCICIO).count(),
                "consultar el catálogo otra vez no debe duplicarla");
    }

    @Test
    void resultAccountCannotBeDeactivated() {
        Account result = system(SystemAccountKey.RESULTADO_DEL_EJERCICIO);
        assertThrows(APIException.class, () -> accountService.setActive(result.getId(), false));
    }

    // --- Bloqueo --------------------------------------------------------------

    @Test
    void manualEntryInClosedPeriodIsRejectedNamingThePeriod() {
        periodService.close(SEP_1, SEP_30);

        APIException ex = assertThrows(APIException.class, () -> sale(SEP_15, "50.00"));
        assertTrue(ex.getMessage().contains("01/09/2025") && ex.getMessage().contains("30/09/2025"),
                ex.getMessage());
    }

    @Test
    void automaticEntryInClosedPeriodIsRejected() {
        periodService.close(SEP_1, SEP_30);

        APIException ex = assertThrows(APIException.class, () -> expense(SEP_15, "50.00"));
        assertTrue(ex.getMessage().contains("está cerrado"), ex.getMessage());
    }

    @Test
    void entryAfterTheClosedPeriodIsAccepted() {
        periodService.close(SEP_1, SEP_30);

        assertDoesNotThrow(() -> sale(OCT_1, "50.00"));
    }

    // --- Reapertura -----------------------------------------------------------

    @Test
    void reopeningReversesTheClosingEntryOnTheSameDateAndUnlocksThePeriod() {
        sale(SEP_15, "1000.00");
        AccountingPeriodDTO closed = periodService.close(SEP_1, SEP_30);

        AccountingPeriodDTO reopened = periodService.reopen(closed.getId());

        assertEquals(AccountingPeriodStatus.REOPENED, reopened.getStatus());
        JournalEntry reversal = journalEntryRepository.findById(reopened.getReversalEntryId()).orElseThrow();
        assertEquals(JournalSourceType.ANULACION_CIERRE, reversal.getSourceType());
        assertEquals(SEP_30, reversal.getEntryDate(),
                "el contra-asiento va con la fecha de la partida de cierre, no la de hoy");
        assertAmount("-1000.00", netIn(reversal, system(SystemAccountKey.INGRESOS_SERVICIOS)));
        assertDoesNotThrow(() -> sale(SEP_15, "50.00"), "reabierto, el período vuelve a aceptar partidas");
    }

    @Test
    void reopeningAPeriodThatIsNotTheLatestIsRejected() {
        AccountingPeriodDTO september = periodService.close(SEP_1, SEP_30);
        periodService.close(OCT_1, OCT_31);

        APIException ex = assertThrows(APIException.class, () -> periodService.reopen(september.getId()));
        assertTrue(ex.getMessage().contains("01/10/2025"), ex.getMessage());
    }

    @Test
    void reopenedPeriodCanBeClosedAgain() {
        sale(SEP_15, "1000.00");
        AccountingPeriodDTO first = periodService.close(SEP_1, SEP_30);
        periodService.reopen(first.getId());
        sale(SEP_15, "200.00");

        AccountingPeriodDTO second = periodService.close(SEP_1, SEP_30);

        assertAmount("1200.00", second.getResultAmount());
        List<AccountingPeriodDTO> periods = periodService.getPeriods();
        assertEquals(2, periods.size(), "el cierre anterior queda en el historial como reabierto");
        assertEquals(second.getId(), periods.get(0).getId(), "a igual rango, el cierre más reciente va primero");
        assertTrue(periods.stream().filter(AccountingPeriodDTO::isReopenable)
                .allMatch(p -> p.getId().equals(second.getId())), "solo el último cerrado se puede reabrir");
    }

    // --- Estado de resultados -------------------------------------------------

    @Test
    void incomeStatementShowsDiscountsAsNegativeIncome() {
        journalService.post(SEP_15, "Venta con descuento", JournalSourceType.MANUAL, null, List.of(
                LinePlan.debit(system(SystemAccountKey.CAJA), amount("900.00")),
                LinePlan.debit(system(SystemAccountKey.DESCUENTOS_VENTAS), amount("100.00")),
                LinePlan.credit(system(SystemAccountKey.INGRESOS_SERVICIOS), amount("1000.00"))));
        expense(SEP_15, "200.00");

        IncomeStatementDTO statement = reportService.getIncomeStatement(SEP_1, SEP_30);

        FinancialStatementLineDTO discounts = statement.getIncomeLines().stream()
                .filter(l -> l.getCode().equals("4201")).findFirst().orElseThrow();
        assertAmount("-100.00", discounts.getAmount());
        assertAmount("900.00", statement.getTotalIncome());
        assertAmount("200.00", statement.getTotalExpenses());
        assertAmount("700.00", statement.getNetResult());
    }

    @Test
    void incomeStatementOfAnEmptyRangeIsZero() {
        IncomeStatementDTO statement = reportService.getIncomeStatement(SEP_1, SEP_30);

        assertTrue(statement.getIncomeLines().isEmpty());
        assertTrue(statement.getExpenseLines().isEmpty());
        assertAmount("0", statement.getNetResult());
    }

    @Test
    void incomeStatementOfClosedAndReopenedPeriodsShowsTheRealFigures() {
        sale(SEP_15, "1000.00");
        expense(SEP_15, "300.00");
        AccountingPeriodDTO closed = periodService.close(SEP_1, SEP_30);

        assertAmount("700.00", reportService.getIncomeStatement(SEP_1, SEP_30).getNetResult(),
                "la partida de cierre no debe dejar el período en cero");

        periodService.reopen(closed.getId());
        IncomeStatementDTO afterReopen = reportService.getIncomeStatement(SEP_1, SEP_30);
        assertAmount("1000.00", afterReopen.getTotalIncome(), "el contra-asiento no debe duplicar el período");
        assertAmount("700.00", afterReopen.getNetResult());
    }

    @Test
    void incomeStatementIncludesDeactivatedAccountsWithMovements() {
        expense(SEP_15, "100.00");
        Account reactivos = byCode("5104");
        reactivos.setActive(false);
        accountRepository.save(reactivos);

        IncomeStatementDTO statement = reportService.getIncomeStatement(SEP_1, SEP_30);

        assertTrue(statement.getExpenseLines().stream().anyMatch(l -> l.getCode().equals("5104")));
    }

    // --- Balance general ------------------------------------------------------

    private void capitalContribution(LocalDate date, String value) {
        journalService.post(date, "Aporte", JournalSourceType.MANUAL, null, List.of(
                LinePlan.debit(system(SystemAccountKey.BANCOS), amount(value)),
                LinePlan.credit(system(SystemAccountKey.CAPITAL), amount(value))));
    }

    private void assertBalanced(BalanceSheetDTO sheet) {
        assertTrue(sheet.isBalanced(), "activo " + sheet.getTotalAssets() + " ≠ pasivo "
                + sheet.getTotalLiabilities() + " + capital " + sheet.getTotalEquity());
        assertEquals(0, sheet.getTotalAssets()
                .compareTo(sheet.getTotalLiabilities().add(sheet.getTotalEquity())));
    }

    @Test
    void balanceSheetBalancesWithoutClosedPeriods() {
        capitalContribution(SEP_1, "5000.00");
        sale(SEP_15, "1000.00");
        expense(SEP_15, "300.00");

        BalanceSheetDTO sheet = reportService.getBalanceSheet(SEP_30);

        assertBalanced(sheet);
        assertAmount("5700.00", sheet.getTotalAssets());
        assertAmount("700.00", sheet.getPeriodResult());
        assertTrue(sheet.getLiabilityLines().isEmpty(), "las cuentas sin movimiento no aparecen");
    }

    @Test
    void balanceSheetDoesNotCountClosedResultsTwice() {
        capitalContribution(SEP_1, "5000.00");
        sale(SEP_15, "1000.00");
        expense(SEP_15, "300.00");
        periodService.close(SEP_1, SEP_30);
        sale(OCT_10, "400.00");

        BalanceSheetDTO sheet = reportService.getBalanceSheet(OCT_31);

        assertBalanced(sheet);
        assertAmount("400.00", sheet.getPeriodResult(), "septiembre ya se trasladó a capital");
        FinancialStatementLineDTO result = sheet.getEquityLines().stream()
                .filter(l -> l.getCode().equals("3201")).findFirst().orElseThrow();
        assertAmount("700.00", result.getAmount());
    }

    @Test
    void balanceSheetBalancesWhenAnEarlierRangeWasNeverClosed() {
        sale(SEP_15, "1000.00");
        sale(OCT_10, "400.00");
        periodService.close(OCT_1, OCT_31);

        BalanceSheetDTO sheet = reportService.getBalanceSheet(OCT_31);

        assertBalanced(sheet);
        assertAmount("1000.00", sheet.getPeriodResult(), "septiembre no se cerró y sigue contando");
    }

    // --- Flujo de efectivo ----------------------------------------------------

    @Test
    void cashFlowReconcilesWithCashAndBankBalances() {
        capitalContribution(SEP_1, "5000.00");
        sale(OCT_10, "1000.00");
        expense(OCT_10, "300.00");

        CashFlowDTO flow = reportService.getCashFlow(OCT_1, OCT_31);

        assertAmount("5000.00", flow.getOpeningBalance());
        assertAmount("1000.00", flow.getTotalInflows());
        assertAmount("300.00", flow.getTotalOutflows());
        assertAmount("5700.00", flow.getClosingBalance());
        assertEquals(0, flow.getClosingBalance().compareTo(
                flow.getOpeningBalance().add(flow.getTotalInflows()).subtract(flow.getTotalOutflows())));

        BalanceSheetDTO sheet = reportService.getBalanceSheet(OCT_31);
        BigDecimal cashAndBanks = sheet.getAssetLines().stream()
                .filter(l -> l.getCode().equals("1101") || l.getCode().equals("1102"))
                .map(FinancialStatementLineDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, flow.getClosingBalance().compareTo(cashAndBanks),
                "el saldo final debe cuadrar contra Caja + Bancos");
    }

    @Test
    void cashFlowClassifiesBySourceOfTheEntry() {
        sale(OCT_10, "1000.00");
        expense(OCT_10, "300.00");

        CashFlowDTO flow = reportService.getCashFlow(OCT_1, OCT_31);

        CashFlowRowDTO manualIn = flow.getInflows().stream()
                .filter(r -> r.getCategory() == CashFlowCategory.PARTIDAS_MANUALES).findFirst().orElseThrow();
        assertAmount("1000.00", manualIn.getAmount());
        CashFlowRowDTO expensesOut = flow.getOutflows().stream()
                .filter(r -> r.getCategory() == CashFlowCategory.GASTOS).findFirst().orElseThrow();
        assertAmount("300.00", expensesOut.getAmount());
    }
}
