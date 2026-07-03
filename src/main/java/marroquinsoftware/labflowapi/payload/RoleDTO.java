package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.Permission;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {
    private Long id;

    @NotBlank(message = "El nombre del rol es obligatorio")
    private String name;

    private String description;

    private Set<Permission> permissions = new HashSet<>();

    /** Cantidad de usuarios con este rol; solo lectura, para la UI. */
    private long userCount;
}
