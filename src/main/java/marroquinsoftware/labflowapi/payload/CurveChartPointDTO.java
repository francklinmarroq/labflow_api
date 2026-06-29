package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Un punto de la curva en el reporte: el valor del paciente (y) en una posicion
 * del eje X, junto con el rango de referencia aplicable a ese punto, que el
 * frontend dibuja como banda umbral.
 *
 * lower/upper salen del ReferenceRange aplicable al paciente (sexo/edad) para el
 * parametro de este punto; pueden ser null si el parametro no tiene rango o si
 * el limite es abierto. y es null si el valor del resultado no es numerico.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurveChartPointDTO {
    private BigDecimal x;
    private BigDecimal y;
    private BigDecimal lower;
    private BigDecimal upper;
    private String parameterName;
}
