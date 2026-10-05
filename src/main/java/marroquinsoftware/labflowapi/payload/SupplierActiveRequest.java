package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierActiveRequest {
    @NotNull(message = "Indique si el proveedor queda activo o desactivado")
    private Boolean active;
}
