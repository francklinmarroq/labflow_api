package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.InvoiceStatus;
import marroquinsoftware.labflowapi.payload.CustomerStatementDTO;
import marroquinsoftware.labflowapi.payload.InvoiceDTO;
import marroquinsoftware.labflowapi.payload.InvoicePreviewDTO;
import marroquinsoftware.labflowapi.payload.InvoiceRequest;
import marroquinsoftware.labflowapi.payload.InvoiceResponse;
import marroquinsoftware.labflowapi.payload.PaymentRequest;
import marroquinsoftware.labflowapi.payload.ReceivablesResponse;

import java.time.LocalDate;

public interface InvoiceService {

    /** Cotiza lo que costaría facturar la orden hoy, sin emitir nada. */
    InvoicePreviewDTO previewInvoice(Long orderId);

    /** Emite la factura CAI de una orden, con su partida contable y (si aplica) el pago inicial. */
    InvoiceDTO createInvoice(InvoiceRequest request);

    // tagId filtra por la etiqueta de la orden de la que salió la factura
    // (convenio, campaña…); null = todas.
    InvoiceResponse getAllInvoices(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                   InvoiceStatus status, Long orderId, LocalDate from, LocalDate to, String search,
                                   Long tagId);

    InvoiceDTO getInvoice(Long invoiceId);

    /** Anula la factura: anula sus pagos activos y revierte todo con contra-asientos. */
    InvoiceDTO annulInvoice(Long invoiceId, String reason);

    InvoiceDTO registerPayment(Long invoiceId, PaymentRequest request);

    InvoiceDTO annulPayment(Long invoiceId, Long paymentId, String reason);

    ReceivablesResponse getReceivables(Integer pageNumber, Integer pageSize);

    CustomerStatementDTO getCustomerStatement(Long customerId);
}
