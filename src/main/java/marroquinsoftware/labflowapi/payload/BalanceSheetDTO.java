package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Balance general a una fecha de corte. {@code totalEquity} incluye
 * {@code periodResult}, el resultado aún no trasladado a capital por un cierre.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSheetDTO {
    private LocalDate date;
    private List<FinancialStatementLineDTO> assetLines;
    private List<FinancialStatementLineDTO> liabilityLines;
    private List<FinancialStatementLineDTO> equityLines;
    private BigDecimal periodResult;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal totalEquity;
    /** true si activo = pasivo + capital. */
    private boolean balanced;
}
