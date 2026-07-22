package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    /** Lo que se cobra por esta línea; distinto de listPrice si hubo regalía. */
    private BigDecimal price;
}
