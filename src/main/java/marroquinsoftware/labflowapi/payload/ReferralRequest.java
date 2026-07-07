package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferralRequest {

    @NotBlank(message = "El laboratorio de destino es obligatorio")
    private String destinationLabName;

    private String reason;

    @NotEmpty(message = "Debe seleccionar al menos un examen para remitir")
    private List<Long> labTestIds;
}
