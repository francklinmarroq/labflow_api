package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Flujo de efectivo del rango (método directo simplificado) sobre Caja y
 * Bancos: saldo final = saldo inicial + entradas - salidas.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowDTO {
    private LocalDate from;
    private LocalDate to;
    private BigDecimal openingBalance;
    private List<CashFlowRowDTO> inflows;
    private List<CashFlowRowDTO> outflows;
    private BigDecimal totalInflows;
    private BigDecimal totalOutflows;
    private BigDecimal netFlow;
    private BigDecimal closingBalance;
}
