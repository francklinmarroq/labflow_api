package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo de {@code POST /auth/release-notes-seen}.
 *
 * <p>Lleva la versión y nada más: a quién se le marca sale del token, no del cuerpo.
 * Por eso no existe forma de marcar por otro usuario — no es que se valide, es que
 * el endpoint no sabe recibir a quién.
 *
 * <p>La versión NO se valida como obligatoria a propósito: si viene vacía, el
 * servicio la ignora y deja el marcador como estaba, en vez de responder un error
 * que el cliente no puede hacer nada por resolver.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseNotesSeenRequest {
    private String version;
}
