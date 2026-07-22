package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Account;
import marroquinsoftware.labflowapi.model.AccountType;
import marroquinsoftware.labflowapi.model.Expense;
import marroquinsoftware.labflowapi.model.JournalSourceType;
import marroquinsoftware.labflowapi.payload.ExpenseDTO;
import marroquinsoftware.labflowapi.payload.ExpenseRequest;
import marroquinsoftware.labflowapi.payload.ExpenseResponse;
import marroquinsoftware.labflowapi.repositories.AccountRepository;
import marroquinsoftware.labflowapi.repositories.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class ExpenseServiceImp implements ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JournalService journalService;

    @Override
    @Transactional
    public ExpenseDTO createExpense(ExpenseRequest request) {
        Account account = accountRepository.findById(request.getExpenseAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", "accountId", request.getExpenseAccountId()));
        if (account.getType() != AccountType.GASTO) {
            throw new APIException("La cuenta seleccionada no es una cuenta de gastos.");
        }
        if (!account.isActive()) {
            throw new APIException("La cuenta " + account.getCode() + " — " + account.getName() + " está desactivada.");
        }

        Expense expense = new Expense();
        expense.setExpenseDate(request.getExpenseDate());
        expense.setDescription(request.getDescription().trim());
        expense.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        expense.setExpenseAccount(account);
        expense.setMethod(request.getMethod());
        expense.setCreatedByUsername(currentUsername());
        expense.setCreatedAt(Instant.now());
        expense = expenseRepository.save(expense);

        journalService.post(
                expense.getExpenseDate(),
                "Gasto: " + expense.getDescription(),
                JournalSourceType.GASTO,
                expense.getId(),
                List.of(
                        JournalService.LinePlan.debit(account, expense.getAmount()),
                        JournalService.LinePlan.credit(journalService.cashOrBank(expense.getMethod()), expense.getAmount())));

        return toDTO(expense);
    }

    @Override
    public ExpenseResponse getAllExpenses(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                          LocalDate from, LocalDate to) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<Expense> page = expenseRepository.search(from, to, pageable);
        ExpenseResponse response = new ExpenseResponse();
        response.setContent(page.getContent().stream().map(this::toDTO).toList());
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    @Override
    public ExpenseDTO getExpense(Long expenseId) {
        return toDTO(findExpense(expenseId));
    }

    @Override
    @Transactional
    public ExpenseDTO annulExpense(Long expenseId, String reason) {
        Expense expense = findExpense(expenseId);
        if (expense.isAnnulled()) {
            throw new APIException("Este gasto ya está anulado.");
        }

        journalService.reverse(
                journalService.findSourceEntry(JournalSourceType.GASTO, expense.getId()),
                JournalSourceType.ANULACION_GASTO,
                expense.getId(),
                "Anulación de gasto: " + expense.getDescription());

        expense.setAnnulled(true);
        expense.setAnnulledAt(Instant.now());
        expense.setAnnulledByUsername(currentUsername());
        expense.setAnnulmentReason(reason != null ? reason.trim() : null);
        return toDTO(expenseRepository.save(expense));
    }

    private Expense findExpense(Long expenseId) {
        return expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", "expenseId", expenseId));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private ExpenseDTO toDTO(Expense expense) {
        return new ExpenseDTO(
                expense.getId(),
                expense.getExpenseDate(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getExpenseAccount().getId(),
                expense.getExpenseAccount().getCode(),
                expense.getExpenseAccount().getName(),
                expense.getMethod(),
                expense.getMethod().getLabel(),
                expense.getCreatedByUsername(),
                expense.getCreatedAt(),
                expense.isAnnulled(),
                expense.getAnnulledAt(),
                expense.getAnnulledByUsername(),
                expense.getAnnulmentReason());
    }
}
