package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.AppRole;
import marroquinsoftware.labflowapi.payload.RoleDTO;
import marroquinsoftware.labflowapi.repositories.AppRoleRepository;
import marroquinsoftware.labflowapi.repositories.UserRepository;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

@Service
public class RoleServiceImp implements RoleService {

    @Autowired
    private AppRoleRepository appRoleRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public List<RoleDTO> getAllRoles() {
        return appRoleRepository.findAll(Sort.by("name").ascending())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public RoleDTO createRole(RoleDTO roleDTO) {
        if (appRoleRepository.existsByName(roleDTO.getName())) {
            throw new APIException("Ya existe un rol con el nombre: " + roleDTO.getName());
        }
        AppRole role = new AppRole();
        role.setName(roleDTO.getName());
        role.setDescription(roleDTO.getDescription());
        role.setPermissions(new HashSet<>(roleDTO.getPermissions()));
        return toDto(appRoleRepository.save(role));
    }

    @Override
    public RoleDTO updateRole(RoleDTO roleDTO, Long roleId) {
        AppRole role = loadRole(roleId);
        if (appRoleRepository.existsByNameAndIdNot(roleDTO.getName(), roleId)) {
            throw new APIException("Ya existe un rol con el nombre: " + roleDTO.getName());
        }
        role.setName(roleDTO.getName());
        role.setDescription(roleDTO.getDescription());
        role.setPermissions(new HashSet<>(roleDTO.getPermissions()));
        return toDto(appRoleRepository.save(role));
    }

    @Override
    public RoleDTO deleteRole(Long roleId) {
        AppRole role = loadRole(roleId);
        long usersWithRole = userRepository.countByAppRole_Id(roleId);
        if (usersWithRole > 0) {
            throw new APIException("No se puede eliminar el rol porque tiene " + usersWithRole
                    + " usuario(s) asignado(s). Reasigne esos usuarios primero.");
        }
        appRoleRepository.delete(role);
        return toDto(role);
    }

    /**
     * Carga el rol verificando que pertenezca al laboratorio en sesión: además
     * del filtro de Hibernate por @TenantId, se comprueba explícitamente para
     * no operar nunca sobre roles de otro tenant.
     */
    private AppRole loadRole(Long roleId) {
        AppRole role = appRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "roleId", roleId));
        if (!role.getLaboratoryId().equals(TenantContext.getLaboratoryId())) {
            throw new ResourceNotFoundException("Role", "roleId", roleId);
        }
        return role;
    }

    private RoleDTO toDto(AppRole role) {
        long userCount = role.getId() != null ? userRepository.countByAppRole_Id(role.getId()) : 0;
        return new RoleDTO(role.getId(), role.getName(), role.getDescription(),
                new HashSet<>(role.getPermissions()), userCount);
    }
}
