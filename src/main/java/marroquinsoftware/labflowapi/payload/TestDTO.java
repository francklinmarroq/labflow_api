package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.TestArea;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestDTO {
    private Long id;

    @NotBlank(message = "El nombre del examen es obligatorio")
    private String name;

    private BigDecimal price;

    private BigDecimal cost;

    private TestArea area;
}
