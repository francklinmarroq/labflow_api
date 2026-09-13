package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.InvoiceStatus;
import marroquinsoftware.labflowapi.payload.BillingClientBalanceDTO;
import marroquinsoftware.labflowapi.payload.CustomerStatementDTO;
import marroquinsoftware.labflowapi.payload.InvoiceDTO;
import marroquinsoftware.labflowapi.payload.InvoicePreviewDTO;
import marroquinsoftware.labflowapi.payload.InvoiceRequest;
import marroquinsoftware.labflowapi.payload.InvoiceResponse;
import marroquinsoftware.labflowapi.payload.PaymentRequest;
import marroquinsoftware.labflowapi.payload.ReceivablesResponse;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceService {

    /** Cotiza lo que costaría facturar la orden hoy, sin emitir nada. */
    InvoicePreviewDTO previewInvoice(Long orderId);

    /** Emite la factura CAI de una orden, con su partida contable y (si aplica) el pago inicial. */
    InvoiceDTO createInvoice(InvoiceRequest request);

    // tagId filtra por la etiqueta de la orden de la que salió la factura
    // (convenio, campaña…); null = todas. billingClientId filtra por la empresa o
    // aseguradora a la que se emitió; null = todas, las de pacientes incluidas.
    InvoiceResponse getAllInvoices(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                   InvoiceStatus status, Long orderId, LocalDate from, LocalDate to, String search,
                                   Long tagId, Long billingClientId);

    InvoiceDTO getInvoice(Long invoiceId);

    /** Anula la factura: anula sus pagos activos y revierte todo con contra-asientos. */
    InvoiceDTO annulInvoice(Long invoiceId, String reason);

    InvoiceDTO registerPayment(Long invoiceId, PaymentRequest request);

    InvoiceDTO annulPayment(Long invoiceId, Long paymentId, String reason);

    ReceivablesResponse getReceivables(Integer pageNumber, Integer pageSize);

    CustomerStatementDTO getCustomerStatement(Long customerId);

    /** Estado de cuenta de un cliente de facturación; espejo del de pacientes. */
    CustomerStatementDTO getBillingClientStatement(Long billingClientId);

    /** Cuánto debe cada cliente de facturación con saldo abierto. */
    List<BillingClientBalanceDTO> getReceivablesByBillingClient();
}
