package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta del login cuando el correo pertenece a más de un laboratorio: el
 * usuario ya validó credenciales (lo acredita {@code selectionToken}) y debe
 * elegir en cuál entrar antes de recibir el JWT de sesión.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabSelectionResponse {
    /** Token corto para llamar a POST /auth/login/select. */
    private String selectionToken;
    private List<LabSummary> laboratories;
}
