package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.IsvRate;

import java.math.BigDecimal;

/**
 * Línea de una compra tal como la captura el usuario. Que cantidad y precio sean
 * positivos y que la cuenta sirva lo valida el servicio, que puede decir cuál
 * línea está mal.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseLineRequest {
    @NotBlank(message = "Escriba la descripción de cada línea")
    private String description;

    @NotNull(message = "Indique la cantidad de cada línea")
    private BigDecimal quantity;

    @NotNull(message = "Indique el precio unitario de cada línea")
    private BigDecimal unitPrice;

    @NotNull(message = "Indique la tasa de ISV de cada línea")
    private IsvRate isvRate;

    @NotNull(message = "Seleccione la cuenta de cada línea")
    private Long accountId;
}
