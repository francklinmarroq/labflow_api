package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.User;
import marroquinsoftware.labflowapi.payload.InvitationInfoResponse;

public interface InvitationService {
    /** Valida el token y devuelve los datos a mostrar en la pantalla de aceptación. */
    InvitationInfoResponse getInvitation(String rawToken);

    /** Activa la cuenta fijando la contraseña; devuelve el usuario ya activo. */
    User acceptInvitation(String rawToken, String password);
}
