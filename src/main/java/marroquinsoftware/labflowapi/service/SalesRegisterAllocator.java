package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.InvoiceItemType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reparte los descuentos de una factura entre sus líneas para el registro de
 * ventas detallado. La factura solo guarda montos a nivel factura (descuento
 * por edad aplicado y otra rebaja); el precio especial sale de cada línea.
 *
 * <p>Por línea: precio especial propio + la parte del descuento por edad que
 * le toca según las órdenes de donde salen sus unidades (cada una con el
 * porcentaje de su paciente, escalado a lo que de verdad se rebajó) + la parte
 * de la otra rebaja en proporción a lo cobrado. Se redondea a centavos y la
 * diferencia se asigna a una línea, de modo que la suma de los totales de las
 * líneas es exactamente el total de la factura.
 *
 * <p>Clase pura, sin repositorios, para probar el reparto sin base de datos.
 */
public final class SalesRegisterAllocator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int WORK_SCALE = 10;

    private SalesRegisterAllocator() {
    }

    /** Una línea de la factura: precios unitarios congelados y cantidad. */
    public record Line(Long testId, InvoiceItemType itemType, BigDecimal listPrice,
                       BigDecimal price, BigDecimal quantity) {

        BigDecimal subtotal() {
            return money(nz(listPrice).multiply(nz(quantity)));
        }

        BigDecimal charged() {
            return nz(price).multiply(nz(quantity));
        }
    }

    /** Una orden de la factura: el porcentaje de edad de su paciente y sus unidades por examen. */
    public record OrderUnits(BigDecimal agePercent, Map<Long, Long> unitsByTestId) {
    }

    /** Resultado por línea, en el mismo orden que las líneas recibidas. */
    public record Allocation(BigDecimal subtotal, BigDecimal discount, BigDecimal total) {
    }

    public static List<Allocation> allocate(List<Line> lines, List<OrderUnits> orders,
                                            BigDecimal ageDiscountApplied, BigDecimal otherDiscount,
                                            BigDecimal invoiceTotal) {
        int n = lines.size();
        List<Allocation> result = new ArrayList<>(n);
        if (n == 0) return result;

        BigDecimal[] subtotal = new BigDecimal[n];
        BigDecimal[] special = new BigDecimal[n];
        BigDecimal[] charged = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            Line l = lines.get(i);
            subtotal[i] = l.subtotal();
            charged[i] = l.charged();
            special[i] = money(subtotal[i].subtract(charged[i]));
        }

        BigDecimal[] age = distributeAge(lines, orders, charged, nz(ageDiscountApplied));
        BigDecimal[] other = distribute(nz(otherDiscount), charged, allIndexes(n));

        BigDecimal[] discount = new BigDecimal[n];
        BigDecimal sumTotal = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            discount[i] = special[i].add(age[i]).add(other[i]);
            sumTotal = sumTotal.add(subtotal[i].subtract(discount[i]));
        }

        // Red de seguridad: si por datos viejos o redondeos el detalle no da el
        // total de la factura, la diferencia va a la línea de mayor cobro. No es
        // el camino normal: con el reparto de arriba ya cuadra.
        if (invoiceTotal != null) {
            BigDecimal gap = sumTotal.subtract(money(invoiceTotal));
            if (gap.signum() != 0) {
                int target = largest(charged, allIndexes(n));
                discount[target] = discount[target].add(gap);
            }
        }

        for (int i = 0; i < n; i++) {
            result.add(new Allocation(subtotal[i], discount[i], subtotal[i].subtract(discount[i])));
        }
        return result;
    }

    /**
     * Descuento por edad por línea: la regla de cada orden (unidades de esa
     * orden × precio cobrado × su porcentaje), escalada a lo realmente rebajado.
     * Si las unidades no explican el descuento (datos inconsistentes), se
     * reparte entre las líneas de examen en proporción a lo cobrado.
     */
    private static BigDecimal[] distributeAge(List<Line> lines, List<OrderUnits> orders,
                                              BigDecimal[] charged, BigDecimal applied) {
        int n = lines.size();
        if (applied.signum() == 0) return zeros(n);

        BigDecimal[] rule = zeros(n);
        BigDecimal ruleSum = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            Line l = lines.get(i);
            if (l.itemType() == InvoiceItemType.CONCEPTO || l.testId() == null) continue;
            for (OrderUnits o : orders) {
                BigDecimal pct = nz(o.agePercent());
                if (pct.signum() <= 0 || o.unitsByTestId() == null) continue;
                Long units = o.unitsByTestId().get(l.testId());
                if (units == null || units == 0) continue;
                rule[i] = rule[i].add(nz(l.price()).multiply(BigDecimal.valueOf(units)).multiply(pct)
                        .divide(HUNDRED, WORK_SCALE, RoundingMode.HALF_UP));
            }
            ruleSum = ruleSum.add(rule[i]);
        }

        if (ruleSum.signum() > 0) {
            return distribute(applied, rule, indexesWithWeight(rule));
        }
        List<Integer> exams = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (lines.get(i).itemType() != InvoiceItemType.CONCEPTO) exams.add(i);
        }
        return distribute(applied, charged, exams.isEmpty() ? allIndexes(n) : exams);
    }

    /**
     * Reparte {@code amount} entre {@code targets} en proporción a {@code weights},
     * redondeando a centavos; la diferencia de redondeo va al de mayor peso.
     */
    private static BigDecimal[] distribute(BigDecimal amount, BigDecimal[] weights, List<Integer> targets) {
        BigDecimal[] out = zeros(weights.length);
        if (amount.signum() == 0 || targets.isEmpty()) return out;
        BigDecimal weightSum = BigDecimal.ZERO;
        for (int i : targets) weightSum = weightSum.add(weights[i]);
        if (weightSum.signum() == 0) {
            // Sin pesos (todo cobrado en cero): va entero a la primera línea.
            out[targets.get(0)] = money(amount);
            return out;
        }
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i : targets) {
            out[i] = money(amount.multiply(weights[i]).divide(weightSum, WORK_SCALE, RoundingMode.HALF_UP));
            assigned = assigned.add(out[i]);
        }
        BigDecimal remainder = money(amount).subtract(assigned);
        if (remainder.signum() != 0) {
            int target = largest(weights, targets);
            out[target] = out[target].add(remainder);
        }
        return out;
    }

    private static int largest(BigDecimal[] values, List<Integer> among) {
        int best = among.get(0);
        for (int i : among) {
            if (values[i].compareTo(values[best]) > 0) best = i;
        }
        return best;
    }

    private static List<Integer> indexesWithWeight(BigDecimal[] weights) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            if (weights[i].signum() > 0) out.add(i);
        }
        return out;
    }

    private static List<Integer> allIndexes(int n) {
        List<Integer> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(i);
        return out;
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] out = new BigDecimal[n];
        for (int i = 0; i < n; i++) out[i] = BigDecimal.ZERO.setScale(2);
        return out;
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
