package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.model.PurchaseStatus;
import marroquinsoftware.labflowapi.model.SaleCondition;
import marroquinsoftware.labflowapi.payload.AnnulRequest;
import marroquinsoftware.labflowapi.payload.PurchaseDTO;
import marroquinsoftware.labflowapi.payload.PurchaseRequest;
import marroquinsoftware.labflowapi.payload.PurchaseResponse;
import marroquinsoftware.labflowapi.payload.SupplierPaymentRequest;
import marroquinsoftware.labflowapi.service.PurchaseService;
import marroquinsoftware.labflowapi.service.SupplierPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class PurchaseController {

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private SupplierPaymentService supplierPaymentService;

    @GetMapping("/purchases")
    @PreAuthorize("hasAnyAuthority('PURCHASES_VIEW','PURCHASES_MANAGE')")
    public ResponseEntity<PurchaseResponse> getPurchases(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_PURCHASES_BY) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) SaleCondition condition,
            @RequestParam(required = false) PurchaseStatus status) {
        return new ResponseEntity<>(purchaseService.getPurchases(pageNumber, pageSize, sortBy, sortOrder,
                from, to, supplierId, condition, status), HttpStatus.OK);
    }

    @GetMapping("/purchases/{purchaseId}")
    @PreAuthorize("hasAnyAuthority('PURCHASES_VIEW','PURCHASES_MANAGE')")
    public ResponseEntity<PurchaseDTO> getPurchase(@PathVariable Long purchaseId) {
        return new ResponseEntity<>(purchaseService.getPurchase(purchaseId), HttpStatus.OK);
    }

    @PostMapping("/purchases")
    @PreAuthorize("hasAuthority('PURCHASES_MANAGE')")
    public ResponseEntity<PurchaseDTO> registerPurchase(@Valid @RequestBody PurchaseRequest request) {
        return new ResponseEntity<>(purchaseService.registerPurchase(request), HttpStatus.CREATED);
    }

    // Las compras no se editan ni se borran: se anulan con contra-asiento.
    @PostMapping("/purchases/{purchaseId}/annul")
    @PreAuthorize("hasAuthority('PURCHASES_MANAGE')")
    public ResponseEntity<PurchaseDTO> annulPurchase(@PathVariable Long purchaseId,
                                                     @Valid @RequestBody AnnulRequest request) {
        return new ResponseEntity<>(purchaseService.annulPurchase(purchaseId, request.getReason()), HttpStatus.OK);
    }

    @PostMapping("/purchases/{purchaseId}/payments")
    @PreAuthorize("hasAuthority('PURCHASES_MANAGE')")
    public ResponseEntity<PurchaseDTO> registerPayment(@PathVariable Long purchaseId,
                                                       @Valid @RequestBody SupplierPaymentRequest request) {
        return new ResponseEntity<>(supplierPaymentService.registerPayment(purchaseId, request), HttpStatus.CREATED);
    }

    @PostMapping("/supplier-payments/{paymentId}/annul")
    @PreAuthorize("hasAuthority('PURCHASES_MANAGE')")
    public ResponseEntity<PurchaseDTO> annulPayment(@PathVariable Long paymentId,
                                                    @Valid @RequestBody AnnulRequest request) {
        return new ResponseEntity<>(supplierPaymentService.annulPayment(paymentId, request.getReason()), HttpStatus.OK);
    }
}
