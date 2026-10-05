package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.PurchaseDTO;
import marroquinsoftware.labflowapi.payload.SupplierPaymentRequest;

/**
 * Pagos a proveedores contra compras a crédito. Ambas operaciones devuelven la
 * compra actualizada, con su saldo y estado nuevos.
 */
public interface SupplierPaymentService {

    PurchaseDTO registerPayment(Long purchaseId, SupplierPaymentRequest request);

    PurchaseDTO annulPayment(Long paymentId, String reason);
}
