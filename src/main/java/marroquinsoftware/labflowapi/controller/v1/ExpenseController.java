package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.AnnulRequest;
import marroquinsoftware.labflowapi.payload.ExpenseDTO;
import marroquinsoftware.labflowapi.payload.ExpenseRequest;
import marroquinsoftware.labflowapi.payload.ExpenseResponse;
import marroquinsoftware.labflowapi.service.ExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    @Autowired
    private ExpenseService expenseService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('EXPENSES_VIEW','EXPENSES_MANAGE')")
    public ResponseEntity<ExpenseResponse> getAllExpenses(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_EXPENSES_BY) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ResponseEntity<>(
                expenseService.getAllExpenses(pageNumber, pageSize, sortBy, sortOrder, from, to),
                HttpStatus.OK);
    }

    @GetMapping("/{expenseId}")
    @PreAuthorize("hasAnyAuthority('EXPENSES_VIEW','EXPENSES_MANAGE')")
    public ResponseEntity<ExpenseDTO> getExpense(@PathVariable Long expenseId) {
        return new ResponseEntity<>(expenseService.getExpense(expenseId), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXPENSES_MANAGE')")
    public ResponseEntity<ExpenseDTO> createExpense(@Valid @RequestBody ExpenseRequest request) {
        return new ResponseEntity<>(expenseService.createExpense(request), HttpStatus.CREATED);
    }

    // Los gastos no se editan ni se borran: se anulan con contra-asiento.
    @PostMapping("/{expenseId}/annul")
    @PreAuthorize("hasAuthority('EXPENSES_MANAGE')")
    public ResponseEntity<ExpenseDTO> annulExpense(@PathVariable Long expenseId,
                                                   @Valid @RequestBody AnnulRequest request) {
        return new ResponseEntity<>(expenseService.annulExpense(expenseId, request.getReason()), HttpStatus.OK);
    }
}
