package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.BillingClientDTO;
import marroquinsoftware.labflowapi.payload.BillingClientResponse;
import marroquinsoftware.labflowapi.service.BillingClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing-clients")
public class BillingClientController {

    @Autowired
    private BillingClientService billingClientService;

    // Quien factura necesita el catálogo para elegir a nombre de quién emitir,
    // sin que eso le dé administración del catálogo (igual que en patologías).
    @GetMapping
    @PreAuthorize("hasAnyAuthority('CATALOG_VIEW','INVOICES_VIEW','INVOICES_CREATE')")
    public ResponseEntity<BillingClientResponse> getAllBillingClients(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_BILLING_CLIENTS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        return new ResponseEntity<>(
                billingClientService.getAllBillingClients(pageNumber, pageSize, sortBy, sortOrder), HttpStatus.OK);
    }

    @GetMapping("/{billingClientId}")
    @PreAuthorize("hasAnyAuthority('CATALOG_VIEW','INVOICES_VIEW','INVOICES_CREATE')")
    public ResponseEntity<BillingClientDTO> getBillingClient(@PathVariable Long billingClientId) {
        return new ResponseEntity<>(billingClientService.getBillingClient(billingClientId), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_CREATE')")
    public ResponseEntity<BillingClientDTO> createBillingClient(@Valid @RequestBody BillingClientDTO dto) {
        return new ResponseEntity<>(billingClientService.createBillingClient(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{billingClientId}")
    @PreAuthorize("hasAuthority('CATALOG_EDIT')")
    public ResponseEntity<BillingClientDTO> updateBillingClient(@Valid @RequestBody BillingClientDTO dto,
                                                                @PathVariable Long billingClientId) {
        return new ResponseEntity<>(billingClientService.updateBillingClient(dto, billingClientId), HttpStatus.OK);
    }

    @DeleteMapping("/{billingClientId}")
    @PreAuthorize("hasAuthority('CATALOG_DELETE')")
    public ResponseEntity<BillingClientDTO> deleteBillingClient(@PathVariable Long billingClientId) {
        return new ResponseEntity<>(billingClientService.deleteBillingClient(billingClientId), HttpStatus.OK);
    }
}
