package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.RoleDTO;

import java.util.List;

public interface RoleService {
    List<RoleDTO> getAllRoles();

    RoleDTO createRole(RoleDTO roleDTO);

    RoleDTO updateRole(RoleDTO roleDTO, Long roleId);

    RoleDTO deleteRole(Long roleId);
}
