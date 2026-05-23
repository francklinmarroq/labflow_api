package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.OrderStatus;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabOrderDTO {
    private Long id;
    @NotNull
    private Long customerId;
    private LocalDateTime requestedAt;
    private OrderStatus status;
    private String notes;
}
