package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.payload.CreateUserRequest;
import marroquinsoftware.labflowapi.payload.UpdateUserRequest;
import marroquinsoftware.labflowapi.payload.UserAccountDTO;
import marroquinsoftware.labflowapi.service.UserAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gestión de los usuarios del laboratorio en sesión. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserAdminService userAdminService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('USERS_VIEW', 'USERS_MANAGE')")
    public ResponseEntity<List<UserAccountDTO>> getUsers() {
        return new ResponseEntity<>(userAdminService.getUsers(), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public ResponseEntity<UserAccountDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        return new ResponseEntity<>(userAdminService.createUser(request), HttpStatus.CREATED);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public ResponseEntity<UserAccountDTO> updateUser(@Valid @RequestBody UpdateUserRequest request,
                                                     @PathVariable Long userId) {
        return new ResponseEntity<>(userAdminService.updateUser(request, userId), HttpStatus.OK);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public ResponseEntity<UserAccountDTO> deleteUser(@PathVariable Long userId) {
        return new ResponseEntity<>(userAdminService.deleteUser(userId), HttpStatus.OK);
    }
}
