package marroquinsoftware.labflowapi.payload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferralRequest {

    @NotBlank(message = "El laboratorio de destino es obligatorio")
    private String destinationLabName;

    private String reason;

    @NotEmpty(message = "Debe seleccionar al menos un examen para remitir")
    @Valid
    private List<Item> items;

    /**
     * Cómo se salda el costo con el laboratorio de destino. {@code null} = queda
     * por pagar; con método, se pagó al momento (efectivo → Caja, otro → Bancos).
     */
    private PaymentMethod paymentMethod;

    /** Examen remitido con lo que cobra el laboratorio de destino por hacerlo. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        @NotNull(message = "Indique el examen a remitir")
        private Long labTestId;

        /** Costo del examen remitido; null o 0 = el destino no cobra. */
        @DecimalMin(value = "0.00", message = "El costo de remisión no puede ser negativo")
        private BigDecimal cost;
    }
}
