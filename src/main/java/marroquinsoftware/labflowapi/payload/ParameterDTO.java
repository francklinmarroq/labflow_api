package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParameterDTO {
    private Long id;
    private Long unitId;
    @NotBlank
    private String name;
    private String section;
    private String valueType;
}
