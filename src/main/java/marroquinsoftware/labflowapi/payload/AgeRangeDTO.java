package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgeRangeDTO {
    private Long id;

    @NotBlank
    private String name;

    private Integer minAgeDays;
    private Integer maxAgeDays;
}
