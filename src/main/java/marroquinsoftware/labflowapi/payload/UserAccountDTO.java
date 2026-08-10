package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.Role;

/** Usuario del laboratorio, tal como lo ve la pantalla de gestión de usuarios. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountDTO {
    private Long id;
    private String username;
    /** Nombre de la persona; puede ser null en usuarios antiguos. */
    private String name;
    private boolean enabled;
    private Role role;
    private Long roleId;
    private String roleName;
    /** Fue invitado y aún no acepta (no ha definido su contraseña). */
    private boolean invitationPending;
}
