package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PathologyDTO {
    private Long id;
    @NotBlank(message = "El nombre de la patología es obligatorio")
    private String name;
}
