package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.ChartType;

import java.util.List;

/**
 * Datos del grafico de una corrida (run) cuando su perfil es una curva
 * (TestConfig.chartType = LINE). El frontend lo renderiza con Chart.js: la serie
 * principal son los puntos (x, y) y la banda umbral se arma con lower/upper de
 * cada punto. Solo se incluye en runs cuyo perfil pide grafico.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurveChartDTO {
    private ChartType type;
    private String xAxisLabel;
    private String unit;
    private List<CurveChartPointDTO> points;
}
