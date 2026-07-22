package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Movimiento del mayor: una línea de partida con el saldo corriente de la cuenta. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LedgerMovementDTO {
    private Long entryId;
    private Long entryNumber;
    private LocalDate entryDate;
    private String description;
    private BigDecimal debit;
    private BigDecimal credit;
    /** Saldo de la cuenta después de este movimiento, según su naturaleza. */
    private BigDecimal balance;
}
