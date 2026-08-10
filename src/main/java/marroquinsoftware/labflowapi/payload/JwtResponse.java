package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class JwtResponse {
    private String token;
    private String username;
    /** Nombre de la persona; puede ser null en usuarios antiguos. */
    private String name;
    /** OWNER o STAFF. */
    private String role;
    /** Nombre del rol configurable asignado, o {@code null} para el OWNER. */
    private String roleName;
    private List<String> permissions;
    /** Laboratorio (tenant) activo de esta sesión. */
    private Long laboratoryId;
    private String laboratoryName;
}
