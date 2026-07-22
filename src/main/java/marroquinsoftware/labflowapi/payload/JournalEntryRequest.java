package marroquinsoftware.labflowapi.payload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Partida manual del libro diario. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryRequest {

    @NotNull(message = "Indique la fecha de la partida")
    private LocalDate entryDate;

    @NotBlank(message = "Escriba la descripción de la partida")
    private String description;

    @NotEmpty(message = "La partida necesita al menos una línea")
    @Valid
    private List<JournalLineRequest> lines;
}
