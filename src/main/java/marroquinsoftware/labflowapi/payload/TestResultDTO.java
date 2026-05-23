package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestResultDTO {
    private Long id;
    private Long testRunId;
    @NotNull
    private Long parameterId;
    private String value;
}
