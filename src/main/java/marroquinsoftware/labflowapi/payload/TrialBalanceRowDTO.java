package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.AccountType;

import java.math.BigDecimal;

/** Fila de la balanza de comprobación: sumas y saldos de una cuenta. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrialBalanceRowDTO {
    private Long accountId;
    private String code;
    private String name;
    private AccountType type;
    private String typeLabel;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal debitBalance;
    private BigDecimal creditBalance;
}
