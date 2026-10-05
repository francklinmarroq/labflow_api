package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Un examen del catálogo agregado a la factura sin orden, con sus unidades. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceTestLineRequest {
    @NotNull(message = "Indique el examen")
    private Long testId;

    @NotNull(message = "Indique la cantidad del examen")
    @Min(value = 1, message = "La cantidad de un examen debe ser al menos 1")
    private Integer quantity;
}
