package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Entrada del catálogo de permisos, con módulo y etiqueta para la UI. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionInfoDTO {
    private String name;
    private String module;
    private String label;
}
