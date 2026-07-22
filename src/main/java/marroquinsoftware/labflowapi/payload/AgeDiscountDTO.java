package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.AgeDiscountKind;

import java.math.BigDecimal;

/**
 * Descuento por edad que le tocaría a un paciente con la configuración actual
 * del laboratorio. Lo usa la pantalla de cotización para mostrarlo antes de
 * guardar; al guardar, la API vuelve a calcularlo y ese es el que queda.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgeDiscountDTO {
    private AgeDiscountKind kind;
    private String label;
    /** Porcentaje aplicado (0–100); cero cuando no hay descuento. */
    private BigDecimal percent;
}
