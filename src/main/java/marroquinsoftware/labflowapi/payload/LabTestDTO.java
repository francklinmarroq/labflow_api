package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabTestDTO {
    private Long id;
    private Long orderId;

    @NotNull
    private Long testId;

    private Long testConfigId;

    private String notes;
}
