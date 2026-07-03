package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferralDTO {
    private Long id;
    private Long orderId;
    private Long orderNumber;
    private String destinationLabName;
    private String reason;
    private Instant referredAt;
    private String createdByUsername;
    private List<ReferralItemDTO> items;
}
