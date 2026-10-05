package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.PurchaseStatus;
import marroquinsoftware.labflowapi.model.SaleCondition;
import marroquinsoftware.labflowapi.payload.PurchaseDTO;
import marroquinsoftware.labflowapi.payload.PurchaseRequest;
import marroquinsoftware.labflowapi.payload.PurchaseResponse;

import java.time.LocalDate;

/**
 * Documentos de compra: se registran con su desglose fiscal y su asiento, y no
 * se editan; un error se corrige anulando con contra-asiento.
 */
public interface PurchaseService {

    PurchaseDTO registerPurchase(PurchaseRequest request);

    PurchaseResponse getPurchases(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                  LocalDate from, LocalDate to, Long supplierId,
                                  SaleCondition condition, PurchaseStatus status);

    /** Detalle con líneas, pagos y la partida que generó. */
    PurchaseDTO getPurchase(Long purchaseId);

    /** Rechaza una compra a crédito que todavía tenga pagos vigentes. */
    PurchaseDTO annulPurchase(Long purchaseId, String reason);
}
