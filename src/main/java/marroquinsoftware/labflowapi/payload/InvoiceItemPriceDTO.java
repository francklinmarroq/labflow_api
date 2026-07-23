package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Precio con el que se quiere facturar un examen de la orden, cuando difiere
 * del catálogo (regalías, promociones, precio negociado).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItemPriceDTO {

    /** Id del examen dentro de la orden ({@code LabTest}), no del catálogo. */
    @NotNull(message = "Indique a qué examen de la orden corresponde el precio")
    private Long labTestId;

    @NotNull(message = "Indique el precio del examen")
    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    private BigDecimal price;
}
