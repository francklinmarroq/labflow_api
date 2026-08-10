package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.CreateUserRequest;
import marroquinsoftware.labflowapi.payload.UpdateUserRequest;
import marroquinsoftware.labflowapi.payload.UserAccountDTO;

import java.util.List;

public interface UserAdminService {
    List<UserAccountDTO> getUsers();

    UserAccountDTO createUser(CreateUserRequest request);

    UserAccountDTO resendInvite(Long userId);

    UserAccountDTO updateUser(UpdateUserRequest request, Long userId);

    UserAccountDTO deleteUser(Long userId);

    /** Fija directamente la contraseña de un usuario (no OWNER) del laboratorio en sesión. */
    UserAccountDTO setUserPassword(Long userId, String password);

    /** Envía a un usuario del laboratorio en sesión el correo para restablecer su contraseña. */
    UserAccountDTO sendPasswordReset(Long userId);
}
