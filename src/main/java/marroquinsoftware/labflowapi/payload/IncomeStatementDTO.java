package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Estado de resultados del rango: ingresos menos gastos, sin partidas de cierre. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStatementDTO {
    private LocalDate from;
    private LocalDate to;
    private List<FinancialStatementLineDTO> incomeLines;
    private List<FinancialStatementLineDTO> expenseLines;
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    /** Utilidad si es positivo, pérdida si es negativo. */
    private BigDecimal netResult;
}
