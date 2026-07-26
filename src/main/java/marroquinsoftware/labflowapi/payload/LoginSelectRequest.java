package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Laboratorio elegido en el selector tras el login. */
@Data
public class LoginSelectRequest {
    @NotNull(message = "Debe elegir un laboratorio")
    private Long laboratoryId;
}
