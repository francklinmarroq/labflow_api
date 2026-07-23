package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.model.InvoiceStatus;
import marroquinsoftware.labflowapi.payload.AnnulRequest;
import marroquinsoftware.labflowapi.payload.InvoiceDTO;
import marroquinsoftware.labflowapi.payload.InvoicePreviewDTO;
import marroquinsoftware.labflowapi.payload.InvoiceRequest;
import marroquinsoftware.labflowapi.payload.InvoiceResponse;
import marroquinsoftware.labflowapi.payload.PaymentRequest;
import marroquinsoftware.labflowapi.service.InvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    @Autowired
    private InvoiceService invoiceService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('INVOICES_VIEW','INVOICES_CREATE')")
    public ResponseEntity<InvoiceResponse> getAllInvoices(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_INVOICES_BY) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search) {
        return new ResponseEntity<>(
                invoiceService.getAllInvoices(pageNumber, pageSize, sortBy, sortOrder, status, orderId, from, to, search),
                HttpStatus.OK);
    }

    // La pantalla de emisión enseña qué se cobraría (precios vigentes y
    // descuento) sin exigir permisos de catálogo ni configurar nada.
    @GetMapping("/preview")
    @PreAuthorize("hasAuthority('INVOICES_CREATE')")
    public ResponseEntity<InvoicePreviewDTO> previewInvoice(@RequestParam Long orderId) {
        return new ResponseEntity<>(invoiceService.previewInvoice(orderId), HttpStatus.OK);
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAnyAuthority('INVOICES_VIEW','INVOICES_CREATE')")
    public ResponseEntity<InvoiceDTO> getInvoice(@PathVariable Long invoiceId) {
        return new ResponseEntity<>(invoiceService.getInvoice(invoiceId), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVOICES_CREATE')")
    public ResponseEntity<InvoiceDTO> createInvoice(@Valid @RequestBody InvoiceRequest request) {
        return new ResponseEntity<>(invoiceService.createInvoice(request), HttpStatus.CREATED);
    }

    // Las facturas CAI nunca se borran: se anulan con contra-asiento contable.
    @PostMapping("/{invoiceId}/annul")
    @PreAuthorize("hasAuthority('INVOICES_ANNUL')")
    public ResponseEntity<InvoiceDTO> annulInvoice(@PathVariable Long invoiceId,
                                                   @Valid @RequestBody AnnulRequest request) {
        return new ResponseEntity<>(invoiceService.annulInvoice(invoiceId, request.getReason()), HttpStatus.OK);
    }

    @PostMapping("/{invoiceId}/payments")
    @PreAuthorize("hasAuthority('PAYMENTS_MANAGE')")
    public ResponseEntity<InvoiceDTO> registerPayment(@PathVariable Long invoiceId,
                                                      @Valid @RequestBody PaymentRequest request) {
        return new ResponseEntity<>(invoiceService.registerPayment(invoiceId, request), HttpStatus.CREATED);
    }

    @PostMapping("/{invoiceId}/payments/{paymentId}/annul")
    @PreAuthorize("hasAuthority('PAYMENTS_MANAGE')")
    public ResponseEntity<InvoiceDTO> annulPayment(@PathVariable Long invoiceId,
                                                   @PathVariable Long paymentId,
                                                   @Valid @RequestBody AnnulRequest request) {
        return new ResponseEntity<>(invoiceService.annulPayment(invoiceId, paymentId, request.getReason()),
                HttpStatus.OK);
    }
}
