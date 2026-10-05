package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Concepto libre de una factura: lo que no es un examen del catálogo (una toma
 * de muestra a domicilio, un cargo de envío). Que tenga descripción y montos
 * positivos lo valida el servicio, que puede decir cuál concepto está mal.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceConceptRequest {
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
}
