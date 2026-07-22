package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.AgeDiscountDTO;
import marroquinsoftware.labflowapi.payload.QuoteDTO;
import marroquinsoftware.labflowapi.payload.QuoteRequest;
import marroquinsoftware.labflowapi.payload.QuoteResponse;

public interface QuoteService {

    QuoteResponse getAllQuotes(Integer pageNumber, Integer pageSize, String sortBy, String sortDir);

    QuoteDTO getQuote(Long quoteId);

    QuoteDTO createQuote(QuoteRequest request);

    void deleteQuote(Long quoteId);

    /** Descuento que aplicaría a esa edad con la configuración actual del laboratorio. */
    AgeDiscountDTO previewDiscount(Integer ageInDays);
}
