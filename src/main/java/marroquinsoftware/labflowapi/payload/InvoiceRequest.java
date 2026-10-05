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

    /**
     * Forma vieja: una sola orden. Equivale a {@code orderIds: [orderId]} y se
     * sigue aceptando para los clientes que no conocen la forma nueva.
     */
    private Long orderId;

    /** Órdenes pendientes de facturar que se incluyen, de cualquier paciente. */
    private List<Long> orderIds;

    /** Exámenes del catálogo agregados sin orden. */
    @Valid
    private List<InvoiceTestLineRequest> tests;

    /** Conceptos libres. */
    private List<InvoiceConceptRequest> concepts;

    /**
     * A nombre de quién se emite. Vacío = la forma de siempre: el cliente de
     * {@code billingClientId} si viene, o el paciente de las órdenes si todas son
     * de uno solo.
     */
    private InvoiceRecipientDTO recipient;

    @NotNull(message = "Seleccione la condición de venta")
    private SaleCondition saleCondition;

    /**
     * Cliente de facturación al que se emite. Vacío = a nombre del paciente de la
     * orden, que es el comportamiento de siempre y el de cualquier cliente de la
     * API que no mande este campo.
     *
     * <p>Cuando viene, el nombre y el RTN de la factura salen de la ficha de ese
     * cliente: el {@code customerRtn} escrito a mano se ignora, para que un
     * documento fiscal no pueda llevar un RTN que contradiga el nombre que imprime.
     */
    private Long billingClientId;

    /**
     * RTN del cliente; vacío = consumidor final (se usa el del expediente si
     * existe). Solo aplica a las facturas a nombre del paciente: con
     * {@code billingClientId} manda el RTN de la ficha del cliente.
     */
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

    /** Forma vieja, de una sola orden; la usan los clientes y tests anteriores. */
    public InvoiceRequest(Long orderId, SaleCondition saleCondition, Long billingClientId, String customerRtn,
                          List<InvoiceItemPriceDTO> itemPrices, BigDecimal total, LocalDate issueDate,
                          PaymentRequest initialPayment) {
        this.orderId = orderId;
        this.saleCondition = saleCondition;
        this.billingClientId = billingClientId;
        this.customerRtn = customerRtn;
        this.itemPrices = itemPrices;
        this.total = total;
        this.issueDate = issueDate;
        this.initialPayment = initialPayment;
    }
}
