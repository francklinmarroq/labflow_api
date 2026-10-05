package marroquinsoftware.labflowapi.controller.v1;

import marroquinsoftware.labflowapi.payload.PayablesAgingDTO;
import marroquinsoftware.labflowapi.payload.PurchaseBookDTO;
import marroquinsoftware.labflowapi.payload.SupplierStatementDTO;
import marroquinsoftware.labflowapi.service.PurchaseReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
public class PurchaseReportController {

    @Autowired
    private PurchaseReportService purchaseReportService;

    @GetMapping("/purchase-book")
    @PreAuthorize("hasAnyAuthority('PURCHASES_VIEW','PURCHASES_MANAGE')")
    public ResponseEntity<PurchaseBookDTO> getPurchaseBook(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ResponseEntity<>(purchaseReportService.getPurchaseBook(from, to), HttpStatus.OK);
    }

    @GetMapping("/supplier-statement")
    @PreAuthorize("hasAnyAuthority('PURCHASES_VIEW','PURCHASES_MANAGE')")
    public ResponseEntity<SupplierStatementDTO> getSupplierStatement(
            @RequestParam Long supplierId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ResponseEntity<>(purchaseReportService.getSupplierStatement(supplierId, from, to), HttpStatus.OK);
    }

    @GetMapping("/payables-aging")
    @PreAuthorize("hasAnyAuthority('PURCHASES_VIEW','PURCHASES_MANAGE')")
    public ResponseEntity<PayablesAgingDTO> getPayablesAging(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return new ResponseEntity<>(purchaseReportService.getPayablesAging(date), HttpStatus.OK);
    }
}
