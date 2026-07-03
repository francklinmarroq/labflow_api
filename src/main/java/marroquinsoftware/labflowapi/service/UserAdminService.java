package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.CreateUserRequest;
import marroquinsoftware.labflowapi.payload.UpdateUserRequest;
import marroquinsoftware.labflowapi.payload.UserAccountDTO;

import java.util.List;

public interface UserAdminService {
    List<UserAccountDTO> getUsers();

    UserAccountDTO createUser(CreateUserRequest request);

    UserAccountDTO updateUser(UpdateUserRequest request, Long userId);

    UserAccountDTO deleteUser(Long userId);
}
