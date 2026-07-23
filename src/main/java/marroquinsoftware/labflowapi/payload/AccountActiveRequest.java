package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountActiveRequest {

    @NotNull(message = "Indique si la cuenta queda activa o desactivada")
    private Boolean active;
}
