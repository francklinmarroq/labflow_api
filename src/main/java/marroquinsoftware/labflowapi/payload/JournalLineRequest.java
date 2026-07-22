package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Línea de una partida manual: la cuenta y su monto en el debe o en el haber
 * (exactamente uno; el servicio valida).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JournalLineRequest {

    @NotNull(message = "Cada línea debe indicar una cuenta")
    private Long accountId;

    private BigDecimal debit;

    private BigDecimal credit;
}
