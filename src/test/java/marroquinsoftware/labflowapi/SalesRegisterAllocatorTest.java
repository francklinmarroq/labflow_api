package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.model.InvoiceItemType;
import marroquinsoftware.labflowapi.service.SalesRegisterAllocator;
import marroquinsoftware.labflowapi.service.SalesRegisterAllocator.Allocation;
import marroquinsoftware.labflowapi.service.SalesRegisterAllocator.Line;
import marroquinsoftware.labflowapi.service.SalesRegisterAllocator.OrderUnits;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cómo el registro de ventas reparte los descuentos de una factura entre sus
 * líneas. La invariante de todos los casos: la suma de los totales de las
 * líneas es el total de la factura, al centavo.
 */
class SalesRegisterAllocatorTest {

    private static final long HEMOGRAMA = 1L;
    private static final long GLUCOSA = 2L;

    private static BigDecimal m(String v) {
        return new BigDecimal(v);
    }

    private static Line exam(long testId, String list, String price, String qty) {
        return new Line(testId, InvoiceItemType.EXAMEN, m(list), m(price), m(qty));
    }

    private static Line concept(String price, String qty) {
        return new Line(null, InvoiceItemType.CONCEPTO, m(price), m(price), m(qty));
    }

    private static OrderUnits order(String percent, Map<Long, Long> units) {
        return new OrderUnits(m(percent), units);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, m(expected).compareTo(actual), "esperado " + expected + " y salió " + actual);
    }

    private static void assertSumsTo(String total, List<Allocation> rows) {
        BigDecimal sum = rows.stream().map(Allocation::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertMoney(total, sum);
    }

    @Test
    void descuentoPorEdadDeUnaSolaOrden() {
        List<Allocation> rows = SalesRegisterAllocator.allocate(
                List.of(exam(HEMOGRAMA, "500", "500", "1"), exam(GLUCOSA, "150", "150", "1")),
                List.of(order("10", Map.of(HEMOGRAMA, 1L, GLUCOSA, 1L))),
                m("65"), null, m("585"));

        assertMoney("50", rows.get(0).discount());
        assertMoney("450", rows.get(0).total());
        assertMoney("15", rows.get(1).discount());
        assertMoney("135", rows.get(1).total());
    }

    @Test
    void lineaAgrupadaDeDosOrdenesConDistintoDescuento() {
        List<Allocation> rows = SalesRegisterAllocator.allocate(
                List.of(exam(HEMOGRAMA, "500", "500", "2")),
                List.of(order("0", Map.of(HEMOGRAMA, 1L)), order("10", Map.of(HEMOGRAMA, 1L))),
                m("50"), null, m("950"));

        assertMoney("1000", rows.get(0).subtotal());
        assertMoney("50", rows.get(0).discount());
        assertMoney("950", rows.get(0).total());
    }

    @Test
    void lasUnidadesSueltasDeUnaLineaNoLlevanDescuentoPorEdad() {
        // "3 — Hemograma": dos de una orden al 10% y uno suelto.
        List<Allocation> rows = SalesRegisterAllocator.allocate(
                List.of(exam(HEMOGRAMA, "500", "500", "3"), concept("200", "1")),
                List.of(order("10", Map.of(HEMOGRAMA, 2L))),
                m("100"), null, m("1600"));

        assertMoney("100", rows.get(0).discount());
        assertMoney("0", rows.get(1).discount());
        assertSumsTo("1600", rows);
    }

    @Test
    void rebajaDelTotalProporcionalAloCobrado() {
        List<Allocation> rows = SalesRegisterAllocator.allocate(
                List.of(concept("300", "1"), concept("100", "1")),
                List.of(), null, m("40"), m("360"));

        assertMoney("270", rows.get(0).total());
        assertMoney("90", rows.get(1).total());
    }

    @Test
    void elRedondeoCuadraAlCentavo() {
        List<Allocation> rows = SalesRegisterAllocator.allocate(
                List.of(concept("100", "1"), concept("100", "1"), concept("100", "1")),
                List.of(), null, m("10"), m("290"));

        BigDecimal discounts = rows.stream().map(Allocation::discount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertMoney("10", discounts);
        assertSumsTo("290", rows);
        rows.forEach(r -> assertEquals(2, r.total().scale()));
    }

    @Test
    void edadRecortadaPorElTotalPedidoSeEscala() {
        // La regla daba 20% (L 130), pero se cobró L 585: solo se rebajaron L 65 por edad.
        List<Allocation> rows = SalesRegisterAllocator.allocate(
                List.of(exam(HEMOGRAMA, "500", "500", "1"), exam(GLUCOSA, "150", "150", "1")),
                List.of(order("20", Map.of(HEMOGRAMA, 1L, GLUCOSA, 1L))),
                m("65"), m("0"), m("585"));

        assertMoney("50", rows.get(0).discount());
        assertMoney("15", rows.get(1).discount());
        assertSumsTo("585", rows);
    }

    @Test
    void precioEspecialVaASuLinea() {
        List<Allocation> rows = SalesRegisterAllocator.allocate(
                List.of(exam(HEMOGRAMA, "500", "450", "2"), exam(GLUCOSA, "150", "150", "1")),
                List.of(), null, null, m("1050"));

        assertMoney("1000", rows.get(0).subtotal());
        assertMoney("100", rows.get(0).discount());
        assertMoney("900", rows.get(0).total());
        assertMoney("0", rows.get(1).discount());
    }

    @Test
    void sinUnidadesQueExpliquenLaEdadSeRepartePorExamenes() {
        List<Allocation> rows = SalesRegisterAllocator.allocate(
                List.of(exam(HEMOGRAMA, "500", "500", "1"), concept("100", "1")),
                List.of(order("10", Map.of())),
                m("30"), null, m("570"));

        assertMoney("30", rows.get(0).discount());
        assertMoney("0", rows.get(1).discount());
        assertSumsTo("570", rows);
    }

    @Test
    void siLosDatosNoCuadranLaDiferenciaVaALaLineaMayor() {
        // Total guardado distinto de lo que dan las líneas (dato viejo): la fila
        // mayor absorbe la diferencia para que el registro no mienta.
        List<Allocation> rows = SalesRegisterAllocator.allocate(
                List.of(exam(HEMOGRAMA, "500", "500", "1"), exam(GLUCOSA, "150", "150", "1")),
                List.of(), null, null, m("640"));

        assertMoney("10", rows.get(0).discount());
        assertSumsTo("640", rows);
    }
}
