package marroquinsoftware.labflowapi.payload;

import lombok.Data;

/** Cambios sobre un usuario existente desde la pantalla de gestión. */
@Data
public class UpdateUserRequest {
    private Long roleId;
    private Boolean enabled;
}
