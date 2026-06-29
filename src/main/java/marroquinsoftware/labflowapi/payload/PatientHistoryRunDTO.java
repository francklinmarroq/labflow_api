package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientHistoryRunDTO {
    private Long runId;
    private Integer runNumber;
    private Instant performedAt;
    private Boolean isVerified;
    private List<PatientHistoryResultDTO> results;
    // Solo presente cuando el perfil del examen es una curva (chartType = LINE);
    // null en el resto, donde el reporte muestra unicamente la tabla de resultados.
    private CurveChartDTO chart;
}
