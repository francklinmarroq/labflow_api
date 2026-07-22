package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Movimiento del estado de cuenta: un cargo (factura) o un abono (pago). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerStatementRowDTO {
    private Instant date;
    /** Descripción lista para mostrar, ej. "Factura Nº 000-001-01-00000042". */
    private String description;
    private BigDecimal charge;
    private BigDecimal payment;
    /** Saldo del cliente después de este movimiento. */
    private BigDecimal balance;
}
