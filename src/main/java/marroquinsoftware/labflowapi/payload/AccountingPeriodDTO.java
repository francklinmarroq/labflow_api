package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.AccountingPeriodStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Período contable con su partida de cierre y, si se reabrió, su contra-asiento. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountingPeriodDTO {
    private Long id;
    private LocalDate startDate;
    private LocalDate endDate;
    private AccountingPeriodStatus status;
    private String statusLabel;
    /** Null si el período se cerró sin ingresos ni gastos que trasladar. */
    private Long closingEntryId;
    private Long closingEntryNumber;
    private Long reversalEntryId;
    private Long reversalEntryNumber;
    /** Utilidad (positivo) o pérdida (negativo) trasladada a "Resultado del ejercicio". */
    private BigDecimal resultAmount;
    private Instant closedAt;
    private String closedByUsername;
    private Instant reopenedAt;
    private String reopenedByUsername;
    /** true solo en el período cerrado más reciente: el único que se puede reabrir. */
    private boolean reopenable;
}
