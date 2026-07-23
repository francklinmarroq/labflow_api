package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.ExpenseDTO;
import marroquinsoftware.labflowapi.payload.ExpenseRequest;
import marroquinsoftware.labflowapi.payload.ExpenseResponse;

import java.time.LocalDate;

public interface ExpenseService {

    ExpenseDTO createExpense(ExpenseRequest request);

    ExpenseResponse getAllExpenses(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                   LocalDate from, LocalDate to);

    ExpenseDTO getExpense(Long expenseId);

    /** Anula el gasto y genera su contra-asiento; los gastos no se editan ni se borran. */
    ExpenseDTO annulExpense(Long expenseId, String reason);
}
