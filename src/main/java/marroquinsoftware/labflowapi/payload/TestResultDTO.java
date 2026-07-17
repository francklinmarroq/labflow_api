package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestResultDTO {
    private Long id;
    private Long testRunId;
    @NotNull(message = "Debe indicar el parámetro del resultado")
    private Long parameterId;
    private String value;

    // Rangos de referencia con los que se reportó este resultado (snapshot
    // congelado al crear la corrida). De solo lectura: el servidor lo calcula y
    // lo devuelve; lo que envíe el cliente al guardar se ignora.
    private List<ReferenceRangeDTO> referenceRanges;
}
