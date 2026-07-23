package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Account;
import marroquinsoftware.labflowapi.model.JournalLine;
import marroquinsoftware.labflowapi.payload.AccountDTO;
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
import java.util.List;
import java.util.Map;
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
