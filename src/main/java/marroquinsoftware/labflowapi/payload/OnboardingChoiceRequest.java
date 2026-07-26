package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Elección del laboratorio sobre los datos de inicio: {@code accept = true}
 * siembra el catálogo por defecto; {@code false} empieza vacío.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingChoiceRequest {
    private boolean accept;
}
