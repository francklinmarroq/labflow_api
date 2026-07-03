package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.payload.RoleDTO;
import marroquinsoftware.labflowapi.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    @Autowired
    private RoleService roleService;

    // USERS_VIEW también puede listar: el form de usuarios necesita el catálogo de roles.
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLES_VIEW', 'ROLES_MANAGE', 'USERS_VIEW', 'USERS_MANAGE')")
    public ResponseEntity<List<RoleDTO>> getAllRoles() {
        return new ResponseEntity<>(roleService.getAllRoles(), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLES_MANAGE')")
    public ResponseEntity<RoleDTO> createRole(@Valid @RequestBody RoleDTO roleDTO) {
        return new ResponseEntity<>(roleService.createRole(roleDTO), HttpStatus.CREATED);
    }

    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLES_MANAGE')")
    public ResponseEntity<RoleDTO> updateRole(@Valid @RequestBody RoleDTO roleDTO, @PathVariable Long roleId) {
        return new ResponseEntity<>(roleService.updateRole(roleDTO, roleId), HttpStatus.OK);
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLES_MANAGE')")
    public ResponseEntity<RoleDTO> deleteRole(@PathVariable Long roleId) {
        return new ResponseEntity<>(roleService.deleteRole(roleId), HttpStatus.OK);
    }
}
