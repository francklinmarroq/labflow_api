package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestConfigDTO {
    private Long id;

    @NotBlank
    private String testTitle;

    @NotBlank
    private String testName;

    @NotEmpty
    private List<Long> parameterIds;

    private boolean active;
}
