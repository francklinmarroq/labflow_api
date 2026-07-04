package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Invitación de un usuario nuevo: el owner solo da correo y rol; la contraseña
 * la define el propio usuario al aceptar la invitación por correo.
 */
@Data
public class CreateUserRequest {
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es válido")
    private String username;

    @NotNull(message = "El rol es obligatorio")
    private Long roleId;
}
