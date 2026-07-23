package marroquinsoftware.labflowapi.controller.v1;

import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.CustomerStatementDTO;
import marroquinsoftware.labflowapi.payload.LedgerReportDTO;
import marroquinsoftware.labflowapi.payload.ReceivablesResponse;
import marroquinsoftware.labflowapi.payload.TrialBalanceDTO;
import marroquinsoftware.labflowapi.service.AccountingReportService;
import marroquinsoftware.labflowapi.service.InvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
public class AccountingReportController {

    @Autowired
    private AccountingReportService accountingReportService;

    @Autowired
    private InvoiceService invoiceService;

    @GetMapping("/ledger")
    @PreAuthorize("hasAuthority('ACCOUNTING_VIEW')")
    public ResponseEntity<LedgerReportDTO> getLedger(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ResponseEntity<>(accountingReportService.getLedger(accountId, from, to), HttpStatus.OK);
    }

    @GetMapping("/trial-balance")
    @PreAuthorize("hasAuthority('ACCOUNTING_VIEW')")
    public ResponseEntity<TrialBalanceDTO> getTrialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ResponseEntity<>(accountingReportService.getTrialBalance(from, to), HttpStatus.OK);
    }

    @GetMapping("/receivables")
    @PreAuthorize("hasAuthority('INVOICES_VIEW')")
    public ResponseEntity<ReceivablesResponse> getReceivables(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize) {
        return new ResponseEntity<>(invoiceService.getReceivables(pageNumber, pageSize), HttpStatus.OK);
    }

    @GetMapping("/customer-statement")
    @PreAuthorize("hasAuthority('INVOICES_VIEW')")
    public ResponseEntity<CustomerStatementDTO> getCustomerStatement(@RequestParam Long customerId) {
        return new ResponseEntity<>(invoiceService.getCustomerStatement(customerId), HttpStatus.OK);
    }
}
