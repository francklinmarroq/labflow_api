package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Un laboratorio al que pertenece el correo, para el selector de inicio de sesión. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabSummary {
    private Long laboratoryId;
    private String laboratoryName;
    /** OWNER o STAFF en ese laboratorio. */
    private String role;
    /** Nombre del rol configurable, o {@code null} para el OWNER. */
    private String roleName;
}
