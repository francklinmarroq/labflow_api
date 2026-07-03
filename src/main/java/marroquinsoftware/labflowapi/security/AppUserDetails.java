package marroquinsoftware.labflowapi.security;

import marroquinsoftware.labflowapi.model.Permission;
import marroquinsoftware.labflowapi.model.Role;
import marroquinsoftware.labflowapi.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * UserDetails que además expone el laboratorio (tenant) del usuario, para
 * poder poblar el {@code TenantContext} en cada petición, y sus permisos
 * efectivos: el OWNER tiene todos los permisos; el STAFF los de su rol.
 */
public class AppUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Long laboratoryId;
    private final Role role;
    private final String roleName;
    private final Set<Permission> permissions;
    private final Collection<? extends GrantedAuthority> authorities;

    public AppUserDetails(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.enabled = user.isEnabled();
        this.laboratoryId = user.getLaboratory() != null ? user.getLaboratory().getId() : null;
        this.role = user.getRole();
        this.roleName = user.getAppRole() != null ? user.getAppRole().getName() : null;
        if (user.getRole() == Role.OWNER) {
            this.permissions = Set.of(Permission.values());
        } else if (user.getAppRole() != null) {
            this.permissions = Set.copyOf(user.getAppRole().getPermissions());
        } else {
            this.permissions = Set.of();
        }
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        permissions.forEach(p -> grantedAuthorities.add(new SimpleGrantedAuthority(p.name())));
        this.authorities = List.copyOf(grantedAuthorities);
    }

    public Long getUserId() {
        return userId;
    }

    public Long getLaboratoryId() {
        return laboratoryId;
    }

    public Role getRole() {
        return role;
    }

    /** Nombre del rol configurable asignado, o {@code null} (p. ej. el OWNER). */
    public String getRoleName() {
        return roleName;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    /** Nombres de los permisos efectivos, para las respuestas de la API. */
    public List<String> getPermissionNames() {
        return permissions.stream().map(Enum::name).sorted().toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
