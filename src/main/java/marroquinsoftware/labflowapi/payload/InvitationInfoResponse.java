package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Datos que ve el usuario invitado en la pantalla de aceptación. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvitationInfoResponse {
    private String email;
    private String laboratoryName;
    private String roleName;
}
