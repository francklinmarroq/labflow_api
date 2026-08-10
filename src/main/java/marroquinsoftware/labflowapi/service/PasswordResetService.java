package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.PasswordResetInfoResponse;

public interface PasswordResetService {

    /**
     * Inicia el restablecimiento para un correo. Anti-enumeración: no revela si el
     * correo existe; solo emite y envía el enlace si hay una cuenta habilitada.
     */
    void requestReset(String email);

    /** Emite un token nuevo y envía el correo de restablecimiento a ese correo (usuario ya conocido). */
    void issueAndSend(String username);

    /** Valida el token (existe y no expiró) y devuelve el correo asociado. */
    PasswordResetInfoResponse getReset(String rawToken);

    /** Valida el token y fija la nueva contraseña en todas las filas del correo. */
    void resetPassword(String rawToken, String password);
}
