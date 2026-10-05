package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Proveedor del laboratorio; el RTN es opcional pero único por laboratorio. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDTO {
    private Long id;

    @NotBlank(message = "El nombre del proveedor es obligatorio")
    private String name;

    private String rtn;
    private String phone;
    private String email;
    private String address;
    private boolean active = true;
}
