package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Cuenta de un estado financiero con su saldo según su naturaleza. Un saldo
 * negativo es un contra-saldo: p. ej. los descuentos sobre ventas restando de
 * los ingresos.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancialStatementLineDTO {
    private Long accountId;
    private String code;
    private String name;
    private BigDecimal amount;
}
