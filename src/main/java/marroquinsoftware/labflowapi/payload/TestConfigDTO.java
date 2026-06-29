package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.ChartType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestConfigDTO {
    private Long id;

    @NotNull
    private Long testId;

    @NotBlank
    private String name;

    @NotEmpty
    private List<Long> parameterIds;

    private boolean active;

    // Presentacion del perfil. NONE (defecto) = solo tabla; LINE = ademas grafico.
    private ChartType chartType;

    // Etiqueta del eje X cuando chartType = LINE (ej. "Tiempo (min)").
    private String chartXAxisLabel;

    // Coordenada X de cada parametro en la curva, indexada por parameterId
    // (ej. {12: 0, 13: 30, 14: 60, 15: 120}). Opcional: solo se usa en perfiles
    // de curva; el orden y la lista de parametros siguen viniendo en parameterIds.
    private Map<Long, BigDecimal> chartXValues;
}
