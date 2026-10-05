package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Account;
import marroquinsoftware.labflowapi.model.AccountType;
import marroquinsoftware.labflowapi.model.AccountingPeriod;
import marroquinsoftware.labflowapi.model.AccountingPeriodStatus;
import marroquinsoftware.labflowapi.model.JournalEntry;
import marroquinsoftware.labflowapi.model.JournalSourceType;
import marroquinsoftware.labflowapi.model.SystemAccountKey;
import marroquinsoftware.labflowapi.payload.AccountingPeriodDTO;
import marroquinsoftware.labflowapi.repositories.AccountRepository;
import marroquinsoftware.labflowapi.repositories.AccountingPeriodRepository;
import marroquinsoftware.labflowapi.repositories.JournalLineRepository;
import marroquinsoftware.labflowapi.service.JournalService.LinePlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccountingPeriodServiceImp implements AccountingPeriodService {

    /** Partidas que trasladan resultados a capital: no son ingresos ni gastos del período. */
    static final List<JournalSourceType> CLOSING_SOURCE_TYPES =
            List.of(JournalSourceType.CIERRE, JournalSourceType.ANULACION_CIERRE);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private AccountingPeriodRepository accountingPeriodRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JournalLineRepository journalLineRepository;

    @Autowired
    private JournalService journalService;

    @Override
    @Transactional(readOnly = true)
    public List<AccountingPeriodDTO> getPeriods() {
        Long reopenableId = accountingPeriodRepository
                .findFirstByStatusOrderByEndDateDesc(AccountingPeriodStatus.CLOSED)
                .map(AccountingPeriod::getId)
                .orElse(null);
        return accountingPeriodRepository.findAllByOrderByEndDateDescIdDesc().stream()
                .map(p -> toDTO(p, p.getId().equals(reopenableId)))
                .toList();
    }

    @Override
    @Transactional
    public AccountingPeriodDTO close(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new APIException("Indique las fechas de inicio y fin del período.");
        }
        if (from.isAfter(to)) {
            throw new APIException("La fecha de inicio del período no puede ser posterior a la fecha de fin.");
        }
        List<AccountingPeriod> overlapping = accountingPeriodRepository
                .findOverlapping(AccountingPeriodStatus.CLOSED, from, to);
        if (!overlapping.isEmpty()) {
            AccountingPeriod conflict = overlapping.get(0);
            throw new APIException("El rango se traslapa con el período ya cerrado del "
                    + DATE_FORMAT.format(conflict.getStartDate()) + " al "
                    + DATE_FORMAT.format(conflict.getEndDate()) + ".");
        }

        List<LinePlan> lines = new ArrayList<>();
        BigDecimal result = BigDecimal.ZERO;
        for (AccountBalance balance : incomeAndExpenseBalances(from, to)) {
            BigDecimal net = balance.net();
            if (net.signum() == 0) continue;
            Account account = balance.account();
            if (account.getType() == AccountType.INGRESO) {
                // Saldo acreedor (lo normal) se salda con un cargo; un contra-ingreso
                // con saldo deudor (descuentos sobre ventas), con un abono.
                lines.add(net.signum() > 0 ? LinePlan.debit(account, net) : LinePlan.credit(account, net.negate()));
                result = result.add(net);
            } else {
                lines.add(net.signum() > 0 ? LinePlan.credit(account, net) : LinePlan.debit(account, net.negate()));
                result = result.subtract(net);
            }
        }

        AccountingPeriod period = new AccountingPeriod();
        period.setStartDate(from);
        period.setEndDate(to);
        period.setStatus(AccountingPeriodStatus.CLOSED);
        period.setResultAmount(result);
        period.setClosedAt(Instant.now());
        period.setClosedByUsername(currentUsername());

        // Sin saldos no hay nada que trasladar: el período se cierra sin partida.
        if (!lines.isEmpty()) {
            Account resultAccount = journalService.systemAccount(SystemAccountKey.RESULTADO_DEL_EJERCICIO);
            if (result.signum() > 0) {
                lines.add(LinePlan.credit(resultAccount, result));
            } else if (result.signum() < 0) {
                lines.add(LinePlan.debit(resultAccount, result.negate()));
            }
            JournalEntry closingEntry = journalService.post(to,
                    "Cierre del período del " + DATE_FORMAT.format(from) + " al " + DATE_FORMAT.format(to),
                    JournalSourceType.CIERRE, null, lines);
            period.setClosingEntry(closingEntry);
        }

        AccountingPeriod saved = accountingPeriodRepository.save(period);
        // La partida apunta a su período, como las automáticas a su documento.
        if (saved.getClosingEntry() != null) {
            saved.getClosingEntry().setSourceId(saved.getId());
        }
        // Se puede cerrar un rango anterior a otro ya cerrado; entonces no es el
        // último y no se puede reabrir hasta reabrir los posteriores.
        boolean isLatest = accountingPeriodRepository
                .findFirstByStatusOrderByEndDateDesc(AccountingPeriodStatus.CLOSED)
                .map(latest -> latest.getId().equals(saved.getId()))
                .orElse(false);
        return toDTO(saved, isLatest);
    }

    @Override
    @Transactional
    public AccountingPeriodDTO reopen(Long periodId) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("AccountingPeriod", "periodId", periodId));
        if (period.getStatus() != AccountingPeriodStatus.CLOSED) {
            throw new APIException("El período ya fue reabierto.");
        }
        AccountingPeriod latest = accountingPeriodRepository
                .findFirstByStatusOrderByEndDateDesc(AccountingPeriodStatus.CLOSED)
                .orElseThrow();
        if (!latest.getId().equals(period.getId())) {
            throw new APIException("Solo se puede reabrir el último período cerrado. Primero reabra el período del "
                    + DATE_FORMAT.format(latest.getStartDate()) + " al "
                    + DATE_FORMAT.format(latest.getEndDate()) + ".");
        }

        if (period.getClosingEntry() != null) {
            // Con la fecha de la partida de cierre, no la de hoy: los movimientos del
            // período no deben reaparecer en el mes en que se reabre.
            JournalEntry reversal = journalService.reverse(period.getClosingEntry(),
                    JournalSourceType.ANULACION_CIERRE, period.getId(),
                    "Reapertura del período del " + DATE_FORMAT.format(period.getStartDate())
                            + " al " + DATE_FORMAT.format(period.getEndDate()),
                    period.getClosingEntry().getEntryDate());
            period.setReversalEntry(reversal);
        }
        period.setStatus(AccountingPeriodStatus.REOPENED);
        period.setReopenedAt(Instant.now());
        period.setReopenedByUsername(currentUsername());
        return toDTO(accountingPeriodRepository.save(period), false);
    }

    /** Saldo neto de cada cuenta de ingreso y gasto en el rango, sin partidas de cierre. */
    private List<AccountBalance> incomeAndExpenseBalances(LocalDate from, LocalDate to) {
        Map<Long, Account> accountsById = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        List<AccountBalance> balances = new ArrayList<>();
        for (Object[] totals : journalLineRepository.totalsByAccountExcluding(from, to, CLOSING_SOURCE_TYPES)) {
            Account account = accountsById.get((Long) totals[0]);
            if (account == null) continue;
            if (account.getType() != AccountType.INGRESO && account.getType() != AccountType.GASTO) continue;
            balances.add(new AccountBalance(account, (BigDecimal) totals[1], (BigDecimal) totals[2]));
        }
        balances.sort(Comparator.comparing(b -> b.account().getCode()));
        return balances;
    }

    /** Saldo de una cuenta según su naturaleza: deudoras debe - haber, acreedoras haber - debe. */
    private record AccountBalance(Account account, BigDecimal debit, BigDecimal credit) {
        BigDecimal net() {
            BigDecimal debitMinusCredit = debit.subtract(credit);
            return account.getType().isDebitNature() ? debitMinusCredit : debitMinusCredit.negate();
        }
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private AccountingPeriodDTO toDTO(AccountingPeriod period, boolean reopenable) {
        JournalEntry closing = period.getClosingEntry();
        JournalEntry reversal = period.getReversalEntry();
        return new AccountingPeriodDTO(
                period.getId(),
                period.getStartDate(),
                period.getEndDate(),
                period.getStatus(),
                period.getStatus().getLabel(),
                closing != null ? closing.getId() : null,
                closing != null ? closing.getEntryNumber() : null,
                reversal != null ? reversal.getId() : null,
                reversal != null ? reversal.getEntryNumber() : null,
                period.getResultAmount(),
                period.getClosedAt(),
                period.getClosedByUsername(),
                period.getReopenedAt(),
                period.getReopenedByUsername(),
                reopenable && period.getStatus() == AccountingPeriodStatus.CLOSED);
    }
}
