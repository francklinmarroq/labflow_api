package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Rango de fechas del período a cerrar, ambos extremos incluidos. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountingPeriodCloseRequest {

    @NotNull(message = "Indique la fecha de inicio del período")
    private LocalDate startDate;

    @NotNull(message = "Indique la fecha de fin del período")
    private LocalDate endDate;
}
