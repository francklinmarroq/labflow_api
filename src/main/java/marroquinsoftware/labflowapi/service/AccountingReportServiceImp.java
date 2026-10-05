package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Account;
import marroquinsoftware.labflowapi.model.AccountType;
import marroquinsoftware.labflowapi.model.CashFlowCategory;
import marroquinsoftware.labflowapi.model.JournalSourceType;
import marroquinsoftware.labflowapi.model.SystemAccountKey;
import marroquinsoftware.labflowapi.model.JournalLine;
import marroquinsoftware.labflowapi.payload.AccountDTO;
import marroquinsoftware.labflowapi.payload.BalanceSheetDTO;
import marroquinsoftware.labflowapi.payload.CashFlowDTO;
import marroquinsoftware.labflowapi.payload.CashFlowRowDTO;
import marroquinsoftware.labflowapi.payload.FinancialStatementLineDTO;
import marroquinsoftware.labflowapi.payload.IncomeStatementDTO;
import marroquinsoftware.labflowapi.payload.LedgerMovementDTO;
import marroquinsoftware.labflowapi.payload.LedgerReportDTO;
import marroquinsoftware.labflowapi.payload.TrialBalanceDTO;
import marroquinsoftware.labflowapi.payload.TrialBalanceRowDTO;
import marroquinsoftware.labflowapi.repositories.AccountRepository;
import marroquinsoftware.labflowapi.repositories.JournalLineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccountingReportServiceImp implements AccountingReportService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JournalLineRepository journalLineRepository;

    @Override
    @Transactional(readOnly = true)
    public LedgerReportDTO getLedger(Long accountId, LocalDate from, LocalDate to) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "accountId", accountId));

        // El saldo se muestra según la naturaleza de la cuenta: en las deudoras
        // (activo, gastos) es debe - haber; en las acreedoras, haber - debe.
        boolean debitNature = account.getType().isDebitNature();
        BigDecimal net = journalLineRepository.netBefore(accountId, from);
        BigDecimal opening = signed(net != null ? net : BigDecimal.ZERO, debitNature);

        BigDecimal running = opening;
        List<LedgerMovementDTO> movements = new ArrayList<>();
        for (JournalLine line : journalLineRepository.movements(accountId, from, to)) {
            BigDecimal delta = signed(line.getDebit().subtract(line.getCredit()), debitNature);
            running = running.add(delta);
            movements.add(new LedgerMovementDTO(
                    line.getEntry().getId(),
                    line.getEntry().getEntryNumber(),
                    line.getEntry().getEntryDate(),
                    line.getEntry().getDescription(),
                    line.getDebit(),
                    line.getCredit(),
                    running));
        }

        return new LedgerReportDTO(toDTO(account), from, to, opening, movements, running);
    }

    @Override
    @Transactional(readOnly = true)
    public TrialBalanceDTO getTrialBalance(LocalDate from, LocalDate to) {
        Map<Long, Account> accountsById = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<TrialBalanceRowDTO> rows = new ArrayList<>();
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalDebitBalances = BigDecimal.ZERO;
        BigDecimal totalCreditBalances = BigDecimal.ZERO;

        for (Object[] totals : journalLineRepository.totalsByAccount(from, to)) {
            Account account = accountsById.get((Long) totals[0]);
            if (account == null) continue;
            BigDecimal debit = (BigDecimal) totals[1];
            BigDecimal credit = (BigDecimal) totals[2];
            BigDecimal net = debit.subtract(credit);
            // Saldo deudor si el neto es positivo; acreedor si es negativo.
            BigDecimal debitBalance = net.compareTo(BigDecimal.ZERO) > 0 ? net : BigDecimal.ZERO;
            BigDecimal creditBalance = net.compareTo(BigDecimal.ZERO) < 0 ? net.negate() : BigDecimal.ZERO;

            rows.add(new TrialBalanceRowDTO(
                    account.getId(),
                    account.getCode(),
                    account.getName(),
                    account.getType(),
                    account.getType().getLabel(),
                    debit,
                    credit,
                    debitBalance,
                    creditBalance));

            totalDebits = totalDebits.add(debit);
            totalCredits = totalCredits.add(credit);
            totalDebitBalances = totalDebitBalances.add(debitBalance);
            totalCreditBalances = totalCreditBalances.add(creditBalance);
        }

        rows.sort(Comparator.comparing(TrialBalanceRowDTO::getCode));
        return new TrialBalanceDTO(from, to, rows,
                totalDebits, totalCredits, totalDebitBalances, totalCreditBalances);
    }

    @Override
    @Transactional(readOnly = true)
    public IncomeStatementDTO getIncomeStatement(LocalDate from, LocalDate to) {
        // Sin la partida de cierre ni su contra-asiento: un período cerrado debe
        // seguir mostrando sus ingresos y gastos reales, no ceros.
        Map<AccountType, List<FinancialStatementLineDTO>> lines = linesByType(
                journalLineRepository.totalsByAccountExcluding(from, to,
                        AccountingPeriodServiceImp.CLOSING_SOURCE_TYPES),
                EnumSet.of(AccountType.INGRESO, AccountType.GASTO), false);
        List<FinancialStatementLineDTO> income = lines.get(AccountType.INGRESO);
        List<FinancialStatementLineDTO> expenses = lines.get(AccountType.GASTO);
        BigDecimal totalIncome = sum(income);
        BigDecimal totalExpenses = sum(expenses);
        return new IncomeStatementDTO(from, to, income, expenses,
                totalIncome, totalExpenses, totalIncome.subtract(totalExpenses));
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceSheetDTO getBalanceSheet(LocalDate date) {
        List<Object[]> totals = journalLineRepository.totalsByAccountUpTo(date);
        Map<AccountType, List<FinancialStatementLineDTO>> lines = linesByType(totals,
                EnumSet.allOf(AccountType.class), true);

        // Resultado aún no trasladado a capital: ingresos - gastos acumulados
        // INCLUYENDO las partidas de cierre. Cada cierre deja en cero los ingresos
        // y gastos de su rango, así que lo ya trasladado no se cuenta dos veces, y
        // un rango anterior que nunca se cerró sigue contando. Por eso el balance
        // cuadra siempre: toda partida está cuadrada.
        BigDecimal periodResult = sum(lines.get(AccountType.INGRESO)).subtract(sum(lines.get(AccountType.GASTO)));

        BigDecimal totalAssets = sum(lines.get(AccountType.ACTIVO));
        BigDecimal totalLiabilities = sum(lines.get(AccountType.PASIVO));
        BigDecimal totalEquity = sum(lines.get(AccountType.CAPITAL)).add(periodResult);
        boolean balanced = totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0;
        return new BalanceSheetDTO(date,
                lines.get(AccountType.ACTIVO),
                lines.get(AccountType.PASIVO),
                lines.get(AccountType.CAPITAL),
                periodResult, totalAssets, totalLiabilities, totalEquity, balanced);
    }

    @Override
    @Transactional(readOnly = true)
    public CashFlowDTO getCashFlow(LocalDate from, LocalDate to) {
        List<Long> cashAccountIds = new ArrayList<>();
        accountRepository.findBySystemKey(SystemAccountKey.CAJA).ifPresent(a -> cashAccountIds.add(a.getId()));
        accountRepository.findBySystemKey(SystemAccountKey.BANCOS).ifPresent(a -> cashAccountIds.add(a.getId()));

        BigDecimal opening = BigDecimal.ZERO;
        Map<CashFlowCategory, BigDecimal> inflows = new EnumMap<>(CashFlowCategory.class);
        Map<CashFlowCategory, BigDecimal> outflows = new EnumMap<>(CashFlowCategory.class);
        if (!cashAccountIds.isEmpty()) {
            BigDecimal net = journalLineRepository.netBeforeForAccounts(cashAccountIds, from);
            opening = net != null ? net : BigDecimal.ZERO;
            for (Object[] row : journalLineRepository.totalsBySourceType(cashAccountIds, from, to)) {
                CashFlowCategory category = CashFlowCategory.of((JournalSourceType) row[0]);
                // Caja y Bancos son de naturaleza deudora: el debe es lo que entra
                // y el haber lo que sale.
                inflows.merge(category, (BigDecimal) row[1], BigDecimal::add);
                outflows.merge(category, (BigDecimal) row[2], BigDecimal::add);
            }
        }

        List<CashFlowRowDTO> inflowRows = cashFlowRows(inflows);
        List<CashFlowRowDTO> outflowRows = cashFlowRows(outflows);
        BigDecimal totalInflows = inflowRows.stream().map(CashFlowRowDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOutflows = outflowRows.stream().map(CashFlowRowDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netFlow = totalInflows.subtract(totalOutflows);
        return new CashFlowDTO(from, to, opening, inflowRows, outflowRows,
                totalInflows, totalOutflows, netFlow, opening.add(netFlow));
    }

    /** Filas en el orden de la clasificación, sin las que no tuvieron movimiento. */
    private List<CashFlowRowDTO> cashFlowRows(Map<CashFlowCategory, BigDecimal> amounts) {
        List<CashFlowRowDTO> rows = new ArrayList<>();
        for (CashFlowCategory category : CashFlowCategory.values()) {
            BigDecimal amount = amounts.get(category);
            if (amount == null || amount.signum() == 0) continue;
            rows.add(new CashFlowRowDTO(category, category.getLabel(), amount));
        }
        return rows;
    }

    /**
     * Agrupa los totales [accountId, debe, haber] por tipo de cuenta, con el
     * saldo según la naturaleza de cada una y ordenados por código. Incluye las
     * cuentas desactivadas que tuvieron movimientos. Siempre devuelve una lista
     * (quizá vacía) para cada tipo pedido.
     */
    private Map<AccountType, List<FinancialStatementLineDTO>> linesByType(
            List<Object[]> totals, Set<AccountType> types, boolean omitZeroBalances) {
        Map<Long, Account> accountsById = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<AccountType, List<FinancialStatementLineDTO>> byType = new EnumMap<>(AccountType.class);
        types.forEach(type -> byType.put(type, new ArrayList<>()));
        for (Object[] row : totals) {
            Account account = accountsById.get((Long) row[0]);
            if (account == null || !types.contains(account.getType())) continue;
            BigDecimal net = ((BigDecimal) row[1]).subtract((BigDecimal) row[2]);
            BigDecimal amount = signed(net, account.getType().isDebitNature());
            if (omitZeroBalances && amount.signum() == 0) continue;
            byType.get(account.getType()).add(new FinancialStatementLineDTO(
                    account.getId(), account.getCode(), account.getName(), amount));
        }
        byType.values().forEach(list -> list.sort(Comparator.comparing(FinancialStatementLineDTO::getCode)));
        return byType;
    }

    private BigDecimal sum(List<FinancialStatementLineDTO> lines) {
        return lines.stream().map(FinancialStatementLineDTO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal signed(BigDecimal debitMinusCredit, boolean debitNature) {
        return debitNature ? debitMinusCredit : debitMinusCredit.negate();
    }

    private AccountDTO toDTO(Account account) {
        return new AccountDTO(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getType(),
                account.getType().getLabel(),
                account.getSystemKey(),
                account.isActive());
    }
}
