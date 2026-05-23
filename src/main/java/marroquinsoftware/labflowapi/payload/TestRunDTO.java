package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRunDTO {
    private Long id;
    private Long testId;
    private Integer runNumber;
    private LocalDateTime performedAt;
    private Boolean isVerified;
    @NotEmpty
    private List<TestResultDTO> results;
}
