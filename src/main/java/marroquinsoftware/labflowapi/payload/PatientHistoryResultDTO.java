package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientHistoryResultDTO {
    private Long id;
    private Long parameterId;
    private String parameterName;
    private String unit;
    private String value;
}
