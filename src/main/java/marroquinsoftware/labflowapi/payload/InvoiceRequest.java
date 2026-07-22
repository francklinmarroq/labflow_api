package marroquinsoftware.labflowapi.payload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.SaleCondition;

/**
 * Datos con los que se emite una factura desde una orden. Los exámenes y
 * precios salen de la orden (catálogo vigente) y el descuento por edad se
 * calcula solo; aquí únicamente se decide la condición de venta, el RTN del
 * cliente si pidió factura con RTN, y el pago inicial.
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
     * Obligatorio y por el total en ventas al contado; opcional (abono) en
     * ventas al crédito.
     */
    @Valid
    private PaymentRequest initialPayment;
}
