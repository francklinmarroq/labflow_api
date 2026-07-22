package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Balanza de comprobación: los totales de débitos y créditos deben coincidir. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrialBalanceDTO {
    private LocalDate from;
    private LocalDate to;
    private List<TrialBalanceRowDTO> rows;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private BigDecimal totalDebitBalances;
    private BigDecimal totalCreditBalances;
}
