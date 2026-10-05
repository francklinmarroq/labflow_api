package marroquinsoftware.labflowapi.payload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que va a contener una factura, para su vista previa: órdenes pendientes,
 * exámenes sueltos, conceptos libres, destinatario, precios especiales por
 * examen y el total que se quiere cobrar. Es lo mismo que la emisión recibe,
 * sin condición de venta, fecha ni pago.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDraftRequest {
    private List<Long> orderIds;
    @Valid
    private List<InvoiceTestLineRequest> tests;
    private List<InvoiceConceptRequest> concepts;
    private InvoiceRecipientDTO recipient;
    @Valid
    private List<InvoiceItemPriceDTO> itemPrices;
    @DecimalMin(value = "0.00", message = "El total a cobrar no puede ser negativo")
    private BigDecimal total;
}
