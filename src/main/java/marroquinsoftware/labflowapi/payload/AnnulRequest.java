package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Motivo con el que se anula un documento (gasto, factura o pago). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnulRequest {

    @NotBlank(message = "Escriba el motivo de la anulación")
    private String reason;
}
