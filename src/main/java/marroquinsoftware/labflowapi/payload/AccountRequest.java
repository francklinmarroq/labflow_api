package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.AccountType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountRequest {

    @NotBlank(message = "Escriba el código de la cuenta")
    @Size(max = 20, message = "El código no puede pasar de 20 caracteres")
    private String code;

    @NotBlank(message = "Escriba el nombre de la cuenta")
    private String name;

    @NotNull(message = "Seleccione el tipo de cuenta")
    private AccountType type;
}
