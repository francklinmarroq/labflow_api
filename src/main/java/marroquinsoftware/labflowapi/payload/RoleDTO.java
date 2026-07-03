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

    /**
     * Cantidad de usuarios con este rol; solo lectura, la calcula el servidor.
     * Se usa Long (no long) para tolerar que el cliente lo mande como null en el
     * body: Jackson 3 falla al mapear null en un primitivo (FAIL_ON_NULL_FOR_
     * PRIMITIVES cambió a true), y este valor se recalcula en el servicio.
     */
    private Long userCount;
}
