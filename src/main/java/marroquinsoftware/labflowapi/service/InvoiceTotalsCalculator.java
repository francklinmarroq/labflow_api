package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Reparte el descuento de una factura entre sus conceptos.
 *
 * <p>El operador puede tocar dos cosas: el precio de cada línea (una regalía se
 * carga en 0.00) y el total final a cobrar. Todo lo demás se deriva, y la regla
 * que manda es que <b>las líneas impresas siempre sumen el total</b>:
 *
 * <pre>
 *   subtotal (precios de lista)
 *   − descuento en líneas   (lista − cobrado, por regalías o precios especiales)
 *   − descuento por edad    (tercera/cuarta edad)
 *   − otros descuentos      (el resto, hasta llegar al total pedido)
 *   = total
 * </pre>
 *
 * <p>El descuento por edad se trata como un techo, no como algo fijo: si al
 * final se rebajó menos de lo que daba la regla, se reporta lo que de verdad se
 * rebajó. Eso evita imprimir un "otros descuentos" negativo cuando en mostrador
 * se otorga menos rebaja que la que le correspondía al paciente.
 */
@Component
public class InvoiceTotalsCalculator {

    /**
     * @param subtotal          bruto, suma de precios de lista
     * @param itemDiscount      rebaja acumulada en las líneas
     * @param ageDiscount       rebaja atribuida al tramo de edad
     * @param otherDiscount     rebaja adicional (promociones, cierre negociado)
     * @param total             lo que se cobra
     */
    public record Totals(
            BigDecimal subtotal,
            BigDecimal itemDiscount,
            BigDecimal ageDiscount,
            BigDecimal otherDiscount,
            BigDecimal total) {
    }

    /**
     * @param listPrices     precio de catálogo de cada línea
     * @param chargedPrices  precio efectivamente cobrado en cada línea (misma posición)
     * @param agePercent     porcentaje del tramo de edad (0–100)
     * @param requestedTotal total que el operador quiere cobrar; null = el calculado
     */
    public Totals compute(List<BigDecimal> listPrices,
                          List<BigDecimal> chargedPrices,
                          BigDecimal agePercent,
                          BigDecimal requestedTotal) {

        BigDecimal subtotal = sum(listPrices);
        BigDecimal charged = sum(chargedPrices);
        BigDecimal itemDiscount = subtotal.subtract(charged);

        // El descuento por edad se calcula sobre lo que queda después de los
        // ajustes de línea: un examen regalado no debe generar descuento de edad.
        BigDecimal ruleDiscount = charged
                .multiply(agePercent != null ? agePercent : BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal total = requestedTotal != null
                ? requestedTotal.setScale(2, RoundingMode.HALF_UP)
                : charged.subtract(ruleDiscount);

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new APIException("El total a cobrar no puede ser negativo.");
        }
        if (total.compareTo(charged) > 0) {
            throw new APIException("El total a cobrar (L " + total + ") no puede superar la suma de los "
                    + "exámenes (L " + charged + "). Suba el precio de las líneas si necesita cobrar más.");
        }

        // Lo realmente rebajado sobre los precios ya ajustados por línea. El
        // tramo de edad se lleva lo suyo hasta donde alcance y el resto queda
        // como otros descuentos; así ninguna de las dos líneas sale negativa.
        BigDecimal givenDiscount = charged.subtract(total);
        BigDecimal ageDiscount = ruleDiscount.min(givenDiscount);
        BigDecimal otherDiscount = givenDiscount.subtract(ageDiscount);

        return new Totals(subtotal, itemDiscount, ageDiscount, otherDiscount, total);
    }

    private BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream()
                .map(a -> a != null ? a : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
