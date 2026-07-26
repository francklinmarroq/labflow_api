package marroquinsoftware.labflowapi.payload;

import lombok.Data;

/**
 * Contraseña que el usuario invitado define al aceptar. Es opcional a nivel de
 * DTO porque, si el correo ya tiene cuenta en otro laboratorio, no se pide (se
 * reusa la existente). La obligatoriedad y el mínimo de 8 caracteres para una
 * identidad nueva se validan en InvitationServiceImp.acceptInvitation.
 */
@Data
public class AcceptInvitationRequest {
    private String password;
}
