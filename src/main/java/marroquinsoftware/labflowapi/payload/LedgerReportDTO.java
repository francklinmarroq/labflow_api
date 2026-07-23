package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Mayor de una cuenta en un rango de fechas. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LedgerReportDTO {
    private AccountDTO account;
    private LocalDate from;
    private LocalDate to;
    private BigDecimal openingBalance;
    private List<LedgerMovementDTO> movements;
    private BigDecimal closingBalance;
}
