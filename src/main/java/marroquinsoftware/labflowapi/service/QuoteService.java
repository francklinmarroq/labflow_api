package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.AgeDiscountDTO;
import marroquinsoftware.labflowapi.payload.QuoteDTO;
import marroquinsoftware.labflowapi.payload.QuoteRequest;
import marroquinsoftware.labflowapi.payload.QuoteResponse;
import marroquinsoftware.labflowapi.payload.RequoteResultDTO;

public interface QuoteService {

    QuoteResponse getAllQuotes(Integer pageNumber, Integer pageSize, String sortBy, String sortDir);

    QuoteDTO getQuote(Long quoteId);

    QuoteDTO createQuote(QuoteRequest request);

    /** Modifica una cotización vigente respetando los precios que ya se cotizaron. */
    QuoteDTO updateQuote(Long quoteId, QuoteRequest request);

    /** Reemplaza una cotización vencida por una nueva a precios actuales. */
    RequoteResultDTO requoteQuote(Long quoteId);

    /**
     * Cierra la cotización como convertida en la orden indicada. Se llama dentro de
     * la transacción que crea la orden: si la cotización no existe o ya no está
     * vigente, lanza una excepción y la orden tampoco se crea.
     */
    void closeWithOrder(Long quoteId, Long orderId, Long orderNumber);

    void deleteQuote(Long quoteId);

    /** Descuento que aplicaría a esa edad con la configuración actual del laboratorio. */
    AgeDiscountDTO previewDiscount(Integer ageInDays);
}
