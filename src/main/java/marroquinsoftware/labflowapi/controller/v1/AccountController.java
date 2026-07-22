package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.payload.AccountActiveRequest;
import marroquinsoftware.labflowapi.payload.AccountDTO;
import marroquinsoftware.labflowapi.payload.AccountRequest;
import marroquinsoftware.labflowapi.payload.AccountUpdateRequest;
import marroquinsoftware.labflowapi.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    @Autowired
    private AccountService accountService;

    // EXPENSES_MANAGE también puede listar: el selector de cuentas de la
    // pantalla de gastos lo necesita sin abrir toda la contabilidad.
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ACCOUNTING_VIEW','ACCOUNTING_MANAGE','EXPENSES_MANAGE')")
    public ResponseEntity<List<AccountDTO>> getAccounts(
            @RequestParam(defaultValue = "false", required = false) boolean activeOnly) {
        return new ResponseEntity<>(accountService.getAccounts(activeOnly), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<AccountDTO> createAccount(@Valid @RequestBody AccountRequest request) {
        return new ResponseEntity<>(accountService.createAccount(request), HttpStatus.CREATED);
    }

    @PutMapping("/{accountId}")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<AccountDTO> updateAccount(@PathVariable Long accountId,
                                                    @Valid @RequestBody AccountUpdateRequest request) {
        return new ResponseEntity<>(accountService.updateAccount(accountId, request), HttpStatus.OK);
    }

    @PutMapping("/{accountId}/active")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<AccountDTO> setActive(@PathVariable Long accountId,
                                                @Valid @RequestBody AccountActiveRequest request) {
        return new ResponseEntity<>(accountService.setActive(accountId, request.getActive()), HttpStatus.OK);
    }
}
