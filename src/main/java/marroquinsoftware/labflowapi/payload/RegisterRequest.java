package marroquinsoftware.labflowapi.payload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es válido")
    @Size(max = 255, message = "El correo no puede tener más de 255 caracteres")
    private String username;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;

    /** Datos del laboratorio que se crea junto con el usuario en el registro. */
    @NotNull(message = "Los datos del laboratorio son obligatorios")
    @Valid
    private LaboratoryDTO laboratory;
}
