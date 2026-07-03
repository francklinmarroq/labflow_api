package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Cambios sobre un usuario existente; la contraseña solo se manda para restablecerla. */
@Data
public class UpdateUserRequest {
    private Long roleId;
    private Boolean enabled;

    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;
}
