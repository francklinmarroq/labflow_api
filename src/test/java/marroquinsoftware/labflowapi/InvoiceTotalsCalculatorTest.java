package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.service.InvoiceTotalsCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cómo se reparte el descuento de una factura entre edad, regalías de línea y
 * el cierre negociado en mostrador. La invariante que se cuida en todos los
 * casos es que las líneas impresas sumen el total.
 */
class InvoiceTotalsCalculatorTest {

    private final InvoiceTotalsCalculator calculator = new InvoiceTotalsCalculator();

    private static List<BigDecimal> money(String... values) {
        return List.of(values).stream().map(BigDecimal::new).toList();
    }

    private InvoiceTotalsCalculator.Totals compute(List<BigDecimal> list, List<BigDecimal> charged,
                                                   String percent, String total) {
        return calculator.compute(list, charged, new BigDecimal(percent),
                total != null ? new BigDecimal(total) : null);
    }

    /** Las líneas siempre deben cuadrar contra el total; si no, la factura miente. */
    private void assertBalances(InvoiceTotalsCalculator.Totals t) {
        assertEquals(0, t.subtotal()
                        .subtract(t.itemDiscount())
                        .subtract(t.ageDiscount())
                        .subtract(t.otherDiscount())
                        .compareTo(t.total()),
                "subtotal − descuentos debe dar el total: " + t);
    }

    @Test
    void sinAjustesSeComportaComoAntes() {
        var t = compute(money("300.00", "200.00"), money("300.00", "200.00"), "20", null);

        assertEquals(0, new BigDecimal("500.00").compareTo(t.subtotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(t.itemDiscount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(t.ageDiscount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(t.otherDiscount()));
        assertEquals(0, new BigDecimal("400.00").compareTo(t.total()));
        assertBalances(t);
    }

    @Test
    void pacienteSinDescuentoDeEdadRecibeUnaRebajaDeMostrador() {
        var t = compute(money("500.00"), money("500.00"), "0", "450.00");

        assertEquals(0, BigDecimal.ZERO.compareTo(t.ageDiscount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(t.otherDiscount()));
        assertBalances(t);
    }

    @Test
    void seOtorgaMasDescuentoQueElDeLaEdad() {
        // Califica al 20% (100.00) pero se cierra en 350: los 50 extra son otros.
        var t = compute(money("500.00"), money("500.00"), "20", "350.00");

        assertEquals(0, new BigDecimal("100.00").compareTo(t.ageDiscount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(t.otherDiscount()));
        assertBalances(t);
    }

    @Test
    void seOtorgaMenosDescuentoQueElDeLaEdadSinProducirNegativos() {
        // Califica al 20% (100.00) pero solo se le rebajan 50. El descuento por
        // edad se reporta por lo realmente rebajado en vez de dejar otros = −50.
        var t = compute(money("500.00"), money("500.00"), "20", "450.00");

        assertEquals(0, new BigDecimal("50.00").compareTo(t.ageDiscount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(t.otherDiscount()));
        assertBalances(t);
    }

    @Test
    void examenDeRegaliaNoGeneraDescuentoDeEdad() {
        // El de 200 va de cortesía; el 20% se calcula solo sobre los 300 que se cobran.
        var t = compute(money("300.00", "200.00"), money("300.00", "0.00"), "20", null);

        assertEquals(0, new BigDecimal("500.00").compareTo(t.subtotal()));
        assertEquals(0, new BigDecimal("200.00").compareTo(t.itemDiscount()));
        assertEquals(0, new BigDecimal("60.00").compareTo(t.ageDiscount()));
        assertEquals(0, new BigDecimal("240.00").compareTo(t.total()));
        assertBalances(t);
    }

    @Test
    void facturaCompletamenteDeCortesiaQuedaEnCero() {
        var t = compute(money("300.00", "200.00"), money("300.00", "200.00"), "0", "0.00");

        assertEquals(0, BigDecimal.ZERO.compareTo(t.total()));
        assertEquals(0, new BigDecimal("500.00").compareTo(t.otherDiscount()));
        assertBalances(t);
    }

    @Test
    void rechazaCobrarMasDeLoQueSumanLasLineas() {
        APIException error = assertThrows(APIException.class,
                () -> compute(money("500.00"), money("500.00"), "0", "600.00"));
        assertEquals(true, error.getMessage().contains("no puede superar"));
    }

    @Test
    void rechazaTotalNegativo() {
        assertThrows(APIException.class,
                () -> compute(money("500.00"), money("500.00"), "0", "-1.00"));
    }
}
