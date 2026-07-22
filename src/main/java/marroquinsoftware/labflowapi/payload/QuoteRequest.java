package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Datos con los que se emite una cotización. El paciente se identifica de una de
 * dos formas: {@code customerId} cuando ya está en el expediente (de ahí se toma
 * el nombre y la edad), o {@code patientName} + {@code patientAgeInDays} cuando
 * todavía no existe y solo se preguntó nombre y edad para el descuento.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuoteRequest {

    private Long customerId;

    private String patientName;

    /** Edad en días, igual que en el expediente del paciente. */
    private Integer patientAgeInDays;

    @NotEmpty(message = "Debe seleccionar al menos un examen para cotizar")
    private List<Long> testIds;

    private String notes;
}
