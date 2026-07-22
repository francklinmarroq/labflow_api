package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.JournalEntryDTO;
import marroquinsoftware.labflowapi.payload.JournalEntryRequest;
import marroquinsoftware.labflowapi.payload.JournalEntryResponse;
import marroquinsoftware.labflowapi.payload.JournalLineDTO;
import marroquinsoftware.labflowapi.payload.JournalLineRequest;
import marroquinsoftware.labflowapi.repositories.AccountRepository;
import marroquinsoftware.labflowapi.repositories.BillingSpecifications;
import marroquinsoftware.labflowapi.repositories.JournalEntryCounterRepository;
import marroquinsoftware.labflowapi.repositories.JournalEntryRepository;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class JournalServiceImp implements JournalService {

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private JournalEntryCounterRepository journalEntryCounterRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountSeeder accountSeeder;

    @Override
    @Transactional
    public JournalEntry post(LocalDate date, String description, JournalSourceType sourceType,
                             Long sourceId, List<LinePlan> lines) {
        return postInternal(date, description, sourceType, sourceId, lines, true);
    }

    @Override
    @Transactional
    public JournalEntry reverse(JournalEntry original, JournalSourceType sourceType,
                                Long sourceId, String description) {
        List<LinePlan> reversed = original.getLines().stream()
                .map(l -> new LinePlan(l.getAccount(), l.getCredit(), l.getDebit()))
                .toList();
        // El contra-asiento no exige cuentas activas: anular siempre tiene que
        // ser posible aunque la cuenta se haya desactivado después del asiento
        // original.
        return postInternal(LocalDate.now(), description, sourceType, sourceId, reversed, false);
    }

    @Override
    public Account systemAccount(SystemAccountKey key) {
        return accountRepository.findBySystemKey(key)
                .orElseGet(() -> {
                    // Laboratorios registrados antes del módulo contable todavía
                    // no tienen catálogo de cuentas: se siembra al primer uso.
                    accountSeeder.seedDefaultAccounts();
                    return accountRepository.findBySystemKey(key)
                            .orElseThrow(() -> new APIException(
                                    "No se encontró la cuenta del sistema " + key + ". Contacte a soporte."));
                });
    }

    @Override
    public Account cashOrBank(PaymentMethod method) {
        return systemAccount(method == PaymentMethod.EFECTIVO
                ? SystemAccountKey.CAJA
                : SystemAccountKey.BANCOS);
    }

    @Override
    public JournalEntry findSourceEntry(JournalSourceType sourceType, Long sourceId) {
        return journalEntryRepository.findFirstBySourceTypeAndSourceId(sourceType, sourceId)
                .orElseThrow(() -> new APIException(
                        "No se encontró la partida contable del documento a anular. Contacte a soporte."));
    }

    @Override
    public JournalEntryResponse getEntries(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                           LocalDate from, LocalDate to, JournalSourceType sourceType) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<JournalEntry> page = journalEntryRepository.findAll(
                BillingSpecifications.journalEntries(from, to, sourceType), pageable);
        JournalEntryResponse response = new JournalEntryResponse();
        response.setContent(page.getContent().stream().map(this::toDTO).toList());
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    @Override
    public JournalEntryDTO getEntry(Long entryId) {
        return toDTO(journalEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("JournalEntry", "entryId", entryId)));
    }

    @Override
    @Transactional
    public JournalEntryDTO createManualEntry(JournalEntryRequest request) {
        List<LinePlan> lines = new ArrayList<>();
        for (JournalLineRequest line : request.getLines()) {
            Account account = accountRepository.findById(line.getAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "accountId", line.getAccountId()));
            lines.add(new LinePlan(account, line.getDebit(), line.getCredit()));
        }
        JournalEntry entry = post(request.getEntryDate(), request.getDescription().trim(),
                JournalSourceType.MANUAL, null, lines);
        return toDTO(entry);
    }

    private JournalEntry postInternal(LocalDate date, String description, JournalSourceType sourceType,
                                      Long sourceId, List<LinePlan> lines, boolean requireActiveAccounts) {
        if (lines == null || lines.size() < 2) {
            throw new APIException("Una partida necesita al menos dos líneas (un cargo y un abono).");
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<JournalLine> entryLines = new ArrayList<>();
        JournalEntry entry = new JournalEntry();

        int order = 0;
        for (LinePlan plan : lines) {
            if (plan.account() == null) {
                throw new APIException("Cada línea de la partida debe indicar una cuenta.");
            }
            if (requireActiveAccounts && !plan.account().isActive()) {
                throw new APIException("La cuenta " + plan.account().getCode() + " — "
                        + plan.account().getName() + " está desactivada.");
            }
            BigDecimal debit = normalize(plan.debit());
            BigDecimal credit = normalize(plan.credit());
            boolean hasDebit = debit.compareTo(BigDecimal.ZERO) > 0;
            boolean hasCredit = credit.compareTo(BigDecimal.ZERO) > 0;
            if (hasDebit == hasCredit) {
                throw new APIException("Cada línea debe llevar un monto en el debe o en el haber, pero no ambos.");
            }

            JournalLine line = new JournalLine();
            line.setEntry(entry);
            line.setAccount(plan.account());
            line.setDebit(debit);
            line.setCredit(credit);
            line.setLineOrder(order++);
            entryLines.add(line);

            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new APIException("La partida no cuadra: débitos L " + totalDebit
                    + ", créditos L " + totalCredit + ".");
        }

        entry.setEntryDate(date != null ? date : LocalDate.now());
        entry.setDescription(description);
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setCreatedAt(Instant.now());
        entry.setCreatedByUsername(currentUsername());
        entry.setLines(entryLines);
        entry.setEntryNumber(nextEntryNumber(requireLaboratoryId()));

        return journalEntryRepository.save(entry);
    }

    private BigDecimal normalize(BigDecimal amount) {
        if (amount == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new APIException("Los montos de la partida no pueden ser negativos.");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Entrega el siguiente correlativo del laboratorio de forma atómica; ver
     * {@link JournalEntryCounter}.
     */
    private Long nextEntryNumber(Long laboratoryId) {
        JournalEntryCounter counter = journalEntryCounterRepository.findById(laboratoryId)
                .orElseGet(() -> {
                    JournalEntryCounter created = new JournalEntryCounter();
                    created.setLaboratoryId(laboratoryId);
                    created.setNextNumber(1L);
                    return created;
                });
        Long number = counter.getNextNumber();
        counter.setNextNumber(number + 1);
        journalEntryCounterRepository.save(counter);
        return number;
    }

    private Long requireLaboratoryId() {
        Long laboratoryId = TenantContext.getLaboratoryId();
        if (laboratoryId == null) {
            throw new APIException("No hay un laboratorio asociado a la sesión actual");
        }
        return laboratoryId;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private JournalEntryDTO toDTO(JournalEntry entry) {
        List<JournalLine> lines = entry.getLines() == null ? List.of() : entry.getLines();
        List<JournalLineDTO> lineDTOs = lines.stream()
                .map(l -> new JournalLineDTO(
                        l.getId(),
                        l.getAccount().getId(),
                        l.getAccount().getCode(),
                        l.getAccount().getName(),
                        l.getDebit(),
                        l.getCredit()))
                .toList();
        BigDecimal totalDebit = lines.stream().map(JournalLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream().map(JournalLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new JournalEntryDTO(
                entry.getId(),
                entry.getEntryNumber(),
                entry.getEntryDate(),
                entry.getDescription(),
                entry.getSourceType(),
                entry.getSourceType() != null ? entry.getSourceType().getLabel() : null,
                entry.getSourceId(),
                entry.getCreatedAt(),
                entry.getCreatedByUsername(),
                totalDebit,
                totalCredit,
                lineDTOs);
    }
}
