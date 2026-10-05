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

    /**
     * Forma vieja: id del examen dentro de la orden ({@code LabTest}). Se traduce
     * a su examen del catálogo, porque el precio especial vale por examen.
     */
    private Long labTestId;
    /** Examen del catálogo al que aplica el precio; vale para todas sus unidades. */
    private Long testId;

    @NotNull(message = "Indique el precio del examen")
    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    private BigDecimal price;

    /** Forma vieja, por examen de la orden. */
    public InvoiceItemPriceDTO(Long labTestId, BigDecimal price) {
        this.labTestId = labTestId;
        this.price = price;
    }
}
