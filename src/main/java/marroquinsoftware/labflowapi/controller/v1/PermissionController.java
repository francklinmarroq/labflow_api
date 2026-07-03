package marroquinsoftware.labflowapi.controller.v1;

import marroquinsoftware.labflowapi.model.Permission;
import marroquinsoftware.labflowapi.payload.PermissionInfoDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Expone el catálogo fijo de permisos para la pantalla de roles. */
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLES_VIEW', 'ROLES_MANAGE')")
    public ResponseEntity<List<PermissionInfoDTO>> getAllPermissions() {
        List<PermissionInfoDTO> permissions = Arrays.stream(Permission.values())
                .map(p -> new PermissionInfoDTO(p.name(), p.getModule(), p.getLabel()))
                .toList();
        return new ResponseEntity<>(permissions, HttpStatus.OK);
    }
}
