package marroquinsoftware.labflowapi;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.AgeDiscountKind;
import marroquinsoftware.labflowapi.model.Quote;
import marroquinsoftware.labflowapi.model.QuoteStatus;
import marroquinsoftware.labflowapi.payload.LaboratoryDTO;
import marroquinsoftware.labflowapi.payload.QuoteDTO;
import marroquinsoftware.labflowapi.payload.QuoteItemDTO;
import marroquinsoftware.labflowapi.payload.QuoteRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vigencia de las cotizaciones y edición de las vigentes: mientras la cotización
 * está vigente se respeta lo cotizado (precios de los exámenes que ya estaban y
 * porcentaje de descuento), y solo lo nuevo entra a precio de catálogo.
 */
class QuoteEditTest extends QuoteTestSupport {

    // --- Vigencia y estado ---

    @Test
    void newQuoteUsesTheDefaultValidityOf15Days() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId()));

        assertEquals(15, quote.getValidityDays());
        assertEquals(today().plusDays(15), quote.getValidUntil());
        assertEquals(QuoteStatus.OPEN, quote.getStatus());
        assertNull(quote.getOrderId());
        assertNull(quote.getRequotedById());
    }

    @Test
    void newQuoteUsesTheLaboratoryValidity() {
        laboratory.setQuoteValidityDays(30);
        laboratoryRepository.save(laboratory);

        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId()));

        assertEquals(30, quote.getValidityDays());
        assertEquals(today().plusDays(30), quote.getValidUntil());
    }

    @Test
    void validityCanBeSetPerQuote() {
        QuoteRequest request = registered(hemograma.getId());
        request.setValidityDays(7);

        QuoteDTO quote = quoteService.createQuote(request);

        assertEquals(today().plusDays(7), quote.getValidUntil());
    }

    @Test
    void creationKeepsNumberTotalsAndDiscount() {
        QuoteDTO first = quoteService.createQuote(registered(hemograma.getId()));
        QuoteDTO second = quoteService.createQuote(unregistered("Don Pedro", 70, hemograma.getId(), glucosa.getId()));

        assertEquals(first.getQuoteNumber() + 1, second.getQuoteNumber());
        assertEquals(0, money("300.00").compareTo(second.getSubtotal()));
        assertEquals(AgeDiscountKind.THIRD_AGE, second.getDiscountKind());
        assertEquals(0, money("75.00").compareTo(second.getDiscountAmount()));
        assertEquals(0, money("225.00").compareTo(second.getTotal()));
    }

    @Test
    void quoteIsOpenThroughItsLastDayAndExpiredTheDayAfter() {
        Quote quote = new Quote();
        quote.setValidUntil(today());
        assertEquals(QuoteStatus.OPEN, QuoteStatus.of(quote, today()));
        assertEquals(QuoteStatus.EXPIRED, QuoteStatus.of(quote, today().plusDays(1)));

        // Las cerradas ganan sobre la fecha.
        quote.setOrderId(1L);
        assertEquals(QuoteStatus.CONVERTED, QuoteStatus.of(quote, today().plusDays(1)));
        quote.setOrderId(null);
        quote.setRequotedById(2L);
        assertEquals(QuoteStatus.REQUOTED, QuoteStatus.of(quote, today().plusDays(1)));
    }

    @Test
    void laboratoryValidityMustBeBetween1And365() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        LaboratoryDTO dto = new LaboratoryDTO();
        dto.setName("Laboratorio");

        dto.setQuoteValidityDays(0);
        assertTrue(hasViolation(validator, dto));
        dto.setQuoteValidityDays(366);
        assertTrue(hasViolation(validator, dto));
        dto.setQuoteValidityDays(30);
        assertFalse(hasViolation(validator, dto));
    }

    private boolean hasViolation(Validator validator, LaboratoryDTO dto) {
        return validator.validate(dto).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("quoteValidityDays"));
    }

    // --- Edición ---

    @Test
    void existingTestKeepsQuotedPriceAndNewTestUsesCatalogPrice() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId()));
        changePrice(hemograma, "250.00");

        QuoteDTO edited = quoteService.updateQuote(quote.getId(), registered(hemograma.getId(), glucosa.getId()));

        assertEquals(quote.getQuoteNumber(), edited.getQuoteNumber());
        assertEquals(quote.getQuotedAt(), edited.getQuotedAt());
        assertEquals(0, money("200.00").compareTo(priceOf(edited, "Hemograma")));
        assertEquals(0, money("100.00").compareTo(priceOf(edited, "Glucosa")));
        assertEquals(0, money("300.00").compareTo(edited.getSubtotal()));
    }

    @Test
    void removingAndAddingBackATestInTheSameEditKeepsItsPrice() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId(), glucosa.getId()));
        changePrice(hemograma, "250.00");

        // El formulario manda la lista final: Hemograma quedó marcado (aunque en
        // otro orden), así que es el mismo examen cotizado.
        QuoteDTO edited = quoteService.updateQuote(quote.getId(), registered(glucosa.getId(), hemograma.getId()));

        assertEquals(0, money("200.00").compareTo(priceOf(edited, "Hemograma")));
    }

    @Test
    void sameDiscountKindKeepsTheQuotedPercent() {
        QuoteDTO quote = quoteService.createQuote(unregistered("Don Pedro", 70, hemograma.getId()));
        laboratory.setThirdAgeDiscountPercent(new BigDecimal("30.00"));
        laboratoryRepository.save(laboratory);

        QuoteDTO edited = quoteService.updateQuote(quote.getId(),
                unregistered("Don Pedro", 70, hemograma.getId(), glucosa.getId()));

        assertEquals(AgeDiscountKind.THIRD_AGE, edited.getDiscountKind());
        assertEquals(0, money("25.00").compareTo(edited.getDiscountPercent()));
        assertEquals(0, money("75.00").compareTo(edited.getDiscountAmount()));
    }

    @Test
    void changedDiscountKindUsesTheCurrentPercent() {
        QuoteDTO quote = quoteService.createQuote(unregistered("Don Pedro", 40, hemograma.getId()));
        assertEquals(AgeDiscountKind.NONE, quote.getDiscountKind());
        laboratory.setThirdAgeDiscountPercent(new BigDecimal("30.00"));
        laboratoryRepository.save(laboratory);

        QuoteDTO edited = quoteService.updateQuote(quote.getId(), unregistered("Don Pedro", 65, hemograma.getId()));

        assertEquals(AgeDiscountKind.THIRD_AGE, edited.getDiscountKind());
        assertEquals(0, money("30.00").compareTo(edited.getDiscountPercent()));
    }

    @Test
    void switchingToAnUnregisteredPatientDropsTheCustomerLink() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId()));

        QuoteDTO edited = quoteService.updateQuote(quote.getId(), unregistered("Otra Persona", 30, hemograma.getId()));

        assertNull(edited.getCustomerId());
        assertEquals("Otra Persona", edited.getPatientName());
    }

    @Test
    void changingValidityCountsFromTheIssueDate() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId()));
        backdate(quote.getId(), 3);
        QuoteRequest request = registered(hemograma.getId());
        request.setValidityDays(30);

        QuoteDTO edited = quoteService.updateQuote(quote.getId(), request);

        assertEquals(today().minusDays(3).plusDays(30), edited.getValidUntil());
    }

    @Test
    void shorteningValidityUntilExpiredIsRejected() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId()));
        backdate(quote.getId(), 10);
        QuoteRequest request = registered(hemograma.getId(), glucosa.getId());
        request.setValidityDays(5);

        APIException ex = assertThrows(APIException.class, () -> quoteService.updateQuote(quote.getId(), request));

        assertTrue(ex.getMessage().contains("quedaría vencida"), ex.getMessage());
        QuoteDTO unchanged = quoteService.getQuote(quote.getId());
        assertEquals(15, unchanged.getValidityDays());
    }

    @Test
    void editWithoutTestsIsRejected() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId()));
        QuoteRequest request = registered();

        APIException ex = assertThrows(APIException.class, () -> quoteService.updateQuote(quote.getId(), request));

        assertTrue(ex.getMessage().contains("al menos un examen"), ex.getMessage());
        assertEquals(1, quoteService.getQuote(quote.getId()).getItems().size());
    }

    @Test
    void expiredQuoteCannotBeEdited() {
        QuoteDTO quote = expiredQuote(registered(hemograma.getId()));

        APIException ex = assertThrows(APIException.class,
                () -> quoteService.updateQuote(quote.getId(), registered(glucosa.getId())));

        assertTrue(ex.getMessage().contains("venció"), ex.getMessage());
        assertTrue(ex.getMessage().contains("recotícela"), ex.getMessage());
    }

    @Test
    void convertedQuoteCannotBeEdited() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId()));
        Quote entity = quoteRepository.findById(quote.getId()).orElseThrow();
        entity.setOrderId(99L);
        entity.setOrderNumber(7L);
        quoteRepository.save(entity);

        APIException ex = assertThrows(APIException.class,
                () -> quoteService.updateQuote(quote.getId(), registered(glucosa.getId())));

        assertTrue(ex.getMessage().contains("orden #7"), ex.getMessage());
    }

    @Test
    void requotedQuoteCannotBeEdited() {
        QuoteDTO quote = expiredQuote(registered(hemograma.getId()));
        QuoteDTO replacement = quoteService.requoteQuote(quote.getId()).getQuote();

        APIException ex = assertThrows(APIException.class,
                () -> quoteService.updateQuote(quote.getId(), registered(glucosa.getId())));

        assertTrue(ex.getMessage().contains("cotización #" + replacement.getQuoteNumber()), ex.getMessage());
    }

    @Test
    void editRecordsTheModification() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId()));
        assertNull(quote.getUpdatedAt());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("recepcion@lab.hn", null, List.of()));
        try {
            QuoteDTO edited = quoteService.updateQuote(quote.getId(), registered(hemograma.getId(), glucosa.getId()));

            assertNotNull(edited.getUpdatedAt());
            assertEquals("recepcion@lab.hn", edited.getUpdatedByUsername());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private BigDecimal priceOf(QuoteDTO quote, String testName) {
        List<QuoteItemDTO> items = quote.getItems();
        return items.stream().filter(i -> i.getTestName().equals(testName)).findFirst().orElseThrow().getPrice();
    }
}
