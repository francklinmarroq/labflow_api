package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.CashFlowCategory;

import java.math.BigDecimal;

/** Entradas o salidas de efectivo de una clasificación. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowRowDTO {
    private CashFlowCategory category;
    private String label;
    private BigDecimal amount;
}
