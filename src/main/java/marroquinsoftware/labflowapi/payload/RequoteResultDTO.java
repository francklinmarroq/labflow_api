package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Resultado de recotizar: la cotización nueva y los nombres de los exámenes de la
 * vencida que ya no están en el catálogo y por eso no pasaron a la nueva.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequoteResultDTO {
    private QuoteDTO quote;
    private List<String> omittedTests;
}
