package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import marroquinsoftware.labflowapi.model.InvoiceItemType;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItemDTO {
    private Long id;
    private Long testId;
    /** Id dentro de la orden; solo viene en la vista previa, para ajustar precios. */
    private Long labTestId;
    private String testName;
    /** Precio de catálogo, para que la factura muestre la rebaja de la línea. */
    private BigDecimal listPrice;
    /** Lo que se cobra por unidad; distinto de listPrice si hubo regalía. */
    private BigDecimal price;
    /** Unidades de la línea: los exámenes iguales se agrupan en una. */
    private BigDecimal quantity;
    private InvoiceItemType itemType;
    /** Importe cobrado de la línea: precio × cantidad. */
    private BigDecimal amount;
}
