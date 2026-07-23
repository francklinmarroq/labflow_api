package marroquinsoftware.labflowapi.payload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.SaleCondition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos con los que se emite una factura desde una orden. Los exámenes salen de
 * la orden y sus precios del catálogo vigente, con el descuento por edad
 * calculado automáticamente; eso es lo que se factura si no se manda nada más.
 *
 * <p>Cuando el mostrador necesita apartarse de ese cálculo puede ajustar el
 * precio de una línea ({@code itemPrices}, p. ej. una regalía en 0.00) y/o
 * fijar el {@code total} a cobrar. La diferencia contra el cálculo automático
 * se registra sola como "otros descuentos"; ver {@code InvoiceTotalsCalculator}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRequest {

    @NotNull(message = "Indique la orden a facturar")
    private Long orderId;

    @NotNull(message = "Seleccione la condición de venta")
    private SaleCondition saleCondition;

    /** RTN del cliente; vacío = consumidor final (se usa el del expediente si existe). */
    private String customerRtn;

    /**
     * Precios especiales por examen. Solo hace falta mandar los que cambian; los
     * exámenes ausentes se facturan al precio de catálogo.
     */
    @Valid
    private List<InvoiceItemPriceDTO> itemPrices;

    /**
     * Total a cobrar. Null = el que sale del cálculo automático. Si es menor, la
     * diferencia queda como "otros descuentos" en la factura.
     */
    @DecimalMin(value = "0.00", message = "El total a cobrar no puede ser negativo")
    private BigDecimal total;

    /**
     * Fecha de emisión. Null = hoy. Permite antedatar la factura; la fecha del
     * asiento contable y del pago inicial siguen esta fecha, no el reloj. No se
     * admiten fechas futuras.
     */
    private LocalDate issueDate;

    /**
     * Obligatorio y por el total en ventas al contado; opcional (abono) en
     * ventas al crédito.
     */
    @Valid
    private PaymentRequest initialPayment;
}
