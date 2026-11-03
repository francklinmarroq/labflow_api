package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.Quote;
import marroquinsoftware.labflowapi.model.QuoteStatus;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.QuoteDTO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Convertir una cotización vigente en orden: la orden y el cierre de la cotización
 * pasan juntos o no pasan.
 *
 * <p>Sin la transacción de la prueba ({@code NOT_SUPPORTED}): así cada llamada al
 * servicio hace commit o rollback de verdad y se puede comprobar que una
 * conversión rechazada no deja una orden suelta. Cada prueba usa su propio
 * laboratorio, así que los conteos no se mezclan entre pruebas.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QuoteToOrderTest extends QuoteTestSupport {

    private LabOrderDTO orderFrom(Long quoteId, Long... testIds) {
        LabOrderDTO dto = new LabOrderDTO();
        dto.setCustomerId(customer.getId());
        dto.setStatus(OrderStatus.PENDING);
        dto.setTestIds(List.of(testIds));
        dto.setQuoteId(quoteId);
        return dto;
    }

    private Quote reload(Long quoteId) {
        return quoteRepository.findById(quoteId).orElseThrow();
    }

    @Test
    void convertingClosesAndLinksTheQuote() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId(), glucosa.getId()));

        LabOrderDTO order = labOrderService.createOrder(orderFrom(quote.getId(), hemograma.getId(), glucosa.getId()));

        Quote closed = reload(quote.getId());
        assertEquals(QuoteStatus.CONVERTED, QuoteStatus.of(closed));
        assertEquals(order.getId(), closed.getOrderId());
        assertEquals(order.getOrderNumber(), closed.getOrderNumber());
    }

    @Test
    void orderMayHaveFewerTestsThanTheQuote() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId(), glucosa.getId()));

        labOrderService.createOrder(orderFrom(quote.getId(), hemograma.getId()));

        assertEquals(QuoteStatus.CONVERTED, QuoteStatus.of(reload(quote.getId())));
    }

    @Test
    void expiredQuoteIsRejectedWithoutCreatingTheOrder() {
        QuoteDTO quote = expiredQuote(registered(hemograma.getId()));
        long ordersBefore = labOrderRepository.count();

        APIException ex = assertThrows(APIException.class,
                () -> labOrderService.createOrder(orderFrom(quote.getId(), hemograma.getId())));

        assertTrue(ex.getMessage().contains("venció"), ex.getMessage());
        assertEquals(ordersBefore, labOrderRepository.count());
        assertEquals(QuoteStatus.EXPIRED, QuoteStatus.of(reload(quote.getId())));
    }

    @Test
    void secondConversionIsRejectedWithoutADuplicateOrder() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId()));
        LabOrderDTO first = labOrderService.createOrder(orderFrom(quote.getId(), hemograma.getId()));
        long ordersBefore = labOrderRepository.count();

        APIException ex = assertThrows(APIException.class,
                () -> labOrderService.createOrder(orderFrom(quote.getId(), hemograma.getId())));

        assertTrue(ex.getMessage().contains("orden #" + first.getOrderNumber()), ex.getMessage());
        assertEquals(ordersBefore, labOrderRepository.count());
        assertEquals(first.getId(), reload(quote.getId()).getOrderId());
    }

    @Test
    void unknownQuoteIsRejectedWithoutCreatingTheOrder() {
        long ordersBefore = labOrderRepository.count();

        assertThrows(ResourceNotFoundException.class,
                () -> labOrderService.createOrder(orderFrom(987654L, hemograma.getId())));

        assertEquals(ordersBefore, labOrderRepository.count());
    }

    @Test
    void failedOrderLeavesTheQuoteOpen() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId()));
        long ordersBefore = labOrderRepository.count();

        // Un examen que no existe hace fallar la creación de la orden.
        assertThrows(RuntimeException.class,
                () -> labOrderService.createOrder(orderFrom(quote.getId(), 987654L)));

        assertEquals(ordersBefore, labOrderRepository.count());
        Quote stillOpen = reload(quote.getId());
        assertEquals(QuoteStatus.OPEN, QuoteStatus.of(stillOpen));
        assertNull(stillOpen.getOrderId());
    }

    @Test
    void orderWithoutQuoteDoesNotTouchQuotes() {
        QuoteDTO quote = quoteService.createQuote(registered(hemograma.getId()));

        labOrderService.createOrder(orderFrom(null, hemograma.getId()));

        assertEquals(QuoteStatus.OPEN, QuoteStatus.of(reload(quote.getId())));
    }
}
