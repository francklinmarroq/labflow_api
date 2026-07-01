package marroquinsoftware.labflowapi.payload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Email
    @Size(max = 255)
    private String username;

    @NotBlank
    @Size(min = 6)
    private String password;

    /** Datos del laboratorio que se crea junto con el usuario en el registro. */
    @NotNull
    @Valid
    private LaboratoryDTO laboratory;
}
