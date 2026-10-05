package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.payload.AccountingPeriodCloseRequest;
import marroquinsoftware.labflowapi.payload.AccountingPeriodDTO;
import marroquinsoftware.labflowapi.service.AccountingPeriodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounting-periods")
public class AccountingPeriodController {

    @Autowired
    private AccountingPeriodService accountingPeriodService;

    @GetMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_VIEW')")
    public ResponseEntity<List<AccountingPeriodDTO>> getPeriods() {
        return new ResponseEntity<>(accountingPeriodService.getPeriods(), HttpStatus.OK);
    }

    @PostMapping("/close")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<AccountingPeriodDTO> close(@Valid @RequestBody AccountingPeriodCloseRequest request) {
        return new ResponseEntity<>(
                accountingPeriodService.close(request.getStartDate(), request.getEndDate()),
                HttpStatus.CREATED);
    }

    @PostMapping("/{periodId}/reopen")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<AccountingPeriodDTO> reopen(@PathVariable Long periodId) {
        return new ResponseEntity<>(accountingPeriodService.reopen(periodId), HttpStatus.OK);
    }
}
