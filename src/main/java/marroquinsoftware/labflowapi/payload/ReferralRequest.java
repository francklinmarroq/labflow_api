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
public class ReferralRequest {

    @NotBlank
    private String destinationLabName;

    private String reason;

    @NotEmpty
    private List<Long> labTestIds;
}
