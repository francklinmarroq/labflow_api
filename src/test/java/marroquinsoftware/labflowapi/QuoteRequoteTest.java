package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.QuoteStatus;
import marroquinsoftware.labflowapi.payload.QuoteDTO;
import marroquinsoftware.labflowapi.payload.RequoteResultDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Recotizar: una cotización vencida se reemplaza por una nueva a precios de hoy, y
 * la vencida queda cerrada apuntando a su reemplazo.
 */
class QuoteRequoteTest extends QuoteTestSupport {

    @Test
    void requoteCreatesANewQuoteAtCurrentPricesAndClosesTheExpiredOne() {
        QuoteDTO expired = expiredQuote(registered(hemograma.getId()));
        changePrice(hemograma, "250.00");

        RequoteResultDTO result = quoteService.requoteQuote(expired.getId());
        QuoteDTO replacement = result.getQuote();

        assertNotEquals(expired.getQuoteNumber(), replacement.getQuoteNumber());
        assertEquals(QuoteStatus.OPEN, replacement.getStatus());
        assertEquals(today().plusDays(15), replacement.getValidUntil());
        assertEquals(0, money("250.00").compareTo(replacement.getSubtotal()));
        assertEquals(expired.getId(), replacement.getRequotedFromId());
        assertEquals(expired.getQuoteNumber(), replacement.getRequotedFromNumber());
        assertTrue(result.getOmittedTests().isEmpty());

        QuoteDTO closed = quoteService.getQuote(expired.getId());
        assertEquals(QuoteStatus.REQUOTED, closed.getStatus());
        assertEquals(replacement.getId(), closed.getRequotedById());
        assertEquals(replacement.getQuoteNumber(), closed.getRequotedByNumber());
    }

    @Test
    void requoteUsesCurrentDiscountConfiguration() {
        QuoteDTO expired = expiredQuote(unregistered("Don Pedro", 70, hemograma.getId()));
        laboratory.setThirdAgeDiscountPercent(new BigDecimal("30.00"));
        laboratoryRepository.save(laboratory);

        QuoteDTO replacement = quoteService.requoteQuote(expired.getId()).getQuote();

        assertEquals("Don Pedro", replacement.getPatientName());
        assertNull(replacement.getCustomerId());
        assertEquals(0, money("30.00").compareTo(replacement.getDiscountPercent()));
    }

    @Test
    void registeredPatientTakesCurrentRecordData() {
        QuoteDTO expired = expiredQuote(registered(hemograma.getId()));
        customer.setName("Ana López de Paz");
        customerRepository.save(customer);

        QuoteDTO replacement = quoteService.requoteQuote(expired.getId()).getQuote();

        assertEquals(customer.getId(), replacement.getCustomerId());
        assertEquals("Ana López de Paz", replacement.getPatientName());
    }

    @Test
    void testsNoLongerInCatalogAreOmittedAndReported() {
        marroquinsoftware.labflowapi.model.Test perfil = newTest("Perfil X", "500.00");
        QuoteDTO expired = expiredQuote(registered(hemograma.getId(), perfil.getId()));
        testRepository.delete(perfil);

        RequoteResultDTO result = quoteService.requoteQuote(expired.getId());

        assertEquals(List.of("Perfil X"), result.getOmittedTests());
        assertEquals(1, result.getQuote().getItems().size());
        assertEquals("Hemograma", result.getQuote().getItems().get(0).getTestName());
    }

    @Test
    void requoteWithNoTestsLeftIsRejected() {
        QuoteDTO expired = expiredQuote(registered(glucosa.getId()));
        testRepository.delete(glucosa);
        long quotesBefore = quoteRepository.count();

        APIException ex = assertThrows(APIException.class, () -> quoteService.requoteQuote(expired.getId()));

        assertTrue(ex.getMessage().contains("sigue en el catálogo"), ex.getMessage());
        assertEquals(quotesBefore, quoteRepository.count());
        assertEquals(QuoteStatus.EXPIRED, quoteService.getQuote(expired.getId()).getStatus());
    }

    @Test
    void openQuoteCannotBeRequoted() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId()));

        APIException ex = assertThrows(APIException.class, () -> quoteService.requoteQuote(quote.getId()));

        assertTrue(ex.getMessage().contains("sigue vigente"), ex.getMessage());
    }

    @Test
    void requotedQuoteCannotBeRequotedAgain() {
        QuoteDTO expired = expiredQuote(registered(hemograma.getId()));
        QuoteDTO replacement = quoteService.requoteQuote(expired.getId()).getQuote();
        long quotesBefore = quoteRepository.count();

        APIException ex = assertThrows(APIException.class, () -> quoteService.requoteQuote(expired.getId()));

        assertTrue(ex.getMessage().contains("cotización #" + replacement.getQuoteNumber()), ex.getMessage());
        assertEquals(quotesBefore, quoteRepository.count());
    }
}
