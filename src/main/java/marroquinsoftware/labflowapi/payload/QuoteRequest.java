package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /**
     * Días de vigencia. Al crear, nulo = la vigencia por defecto del laboratorio;
     * al editar, nulo = se conserva la que tenía.
     */
    @Min(value = 1, message = "La vigencia de la cotización debe ser de al menos 1 día")
    @Max(value = 365, message = "La vigencia de la cotización no puede pasar de 365 días")
    private Integer validityDays;
}
