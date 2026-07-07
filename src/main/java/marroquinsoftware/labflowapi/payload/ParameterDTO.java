package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.ParameterSection;
import marroquinsoftware.labflowapi.model.ParameterValueType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParameterDTO {
    private Long id;
    private Long unitId;
    @NotBlank(message = "El nombre del parámetro es obligatorio")
    private String name;
    private ParameterSection section;
    private ParameterValueType valueType;
}
