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
    /** Nombre de la persona; puede ser null en usuarios antiguos. */
    private String name;
    private String role;
    private String roleName;
    private List<String> permissions;
    /** Laboratorio (tenant) activo de esta sesión. */
    private Long laboratoryId;
    private String laboratoryName;
    /**
     * Última versión de novedades que este usuario ya vio, o null si no ha visto
     * ninguna. El cliente decide con esto si anuncia; la API no la interpreta.
     *
     * <p>VA AL FINAL a propósito, y lo mismo el argumento correspondiente del
     * constructor: {@code @AllArgsConstructor} es posicional y casi todos los campos
     * de arriba son String, así que meterlo en medio correría los argumentos sin que
     * nada falle al compilar ni en ejecución — el usuario simplemente vería el nombre
     * del laboratorio donde va su rol. Si se agrega otro campo, va después de este.
     */
    private String lastSeenReleaseVersion;
}
