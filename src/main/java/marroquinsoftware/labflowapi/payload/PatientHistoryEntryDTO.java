package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.OrderStatus;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientHistoryEntryDTO {
    private Long orderId;
    private Long labTestId;
    private Instant requestedAt;
    private OrderStatus orderStatus;
    private List<PatientHistoryRunDTO> runs;
}
