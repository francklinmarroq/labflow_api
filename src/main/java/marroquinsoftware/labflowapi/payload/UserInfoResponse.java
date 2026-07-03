package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Respuesta de {@code GET /auth/me}: identidad y permisos del usuario en sesión. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    private String username;
    private String role;
    private String roleName;
    private List<String> permissions;
}
