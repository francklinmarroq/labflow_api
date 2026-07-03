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
    private boolean enabled;
    private Role role;
    private Long roleId;
    private String roleName;
}
