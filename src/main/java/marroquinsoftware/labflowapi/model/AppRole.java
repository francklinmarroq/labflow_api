package marroquinsoftware.labflowapi.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * Rol configurable creado por cada laboratorio (tenant). El owner define qué
 * permisos del catálogo {@link Permission} otorga el rol; los usuarios STAFF
 * reciben sus permisos a través del rol asignado.
 *
 * <p>A diferencia de las entidades de negocio, {@code AppRole} NO usa
 * {@code @TenantId}: se carga durante la autenticación (al construir
 * {@code AppUserDetails}), cuando todavía no hay tenant establecido, así que el
 * filtro por tenant fallaría. El aislamiento por laboratorio se hace a mano en
 * {@code RoleServiceImp}/{@code UserAdminServiceImp} (igual que con {@code User}).
 */
@Entity
@Table(name = "app_role", uniqueConstraints = @UniqueConstraint(columnNames = {"laboratory_id", "name"}))
@Data
@NoArgsConstructor
public class AppRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 50)
    private Set<Permission> permissions = new HashSet<>();
}
