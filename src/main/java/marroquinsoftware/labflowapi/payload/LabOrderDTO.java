package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.OrderStatus;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabOrderDTO {
    private Long id;
    private Long orderNumber;
    @NotNull
    private Long customerId;
    private Instant requestedAt;
    private OrderStatus status;
    private String notes;
    private LocalDate lmpDate;
    private boolean pregnant;
    private Integer gestationalWeeks;
    private boolean menopausal;
}
