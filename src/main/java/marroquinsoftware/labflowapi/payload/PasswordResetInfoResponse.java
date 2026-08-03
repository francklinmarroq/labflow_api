package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Datos mínimos para la pantalla de restablecimiento (validar token + mostrar el correo). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetInfoResponse {
    private String email;
}
