package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Movimiento del estado de cuenta de un proveedor: una compra a crédito (cargo)
 * o un pago (abono), con el saldo acumulado después de él.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierStatementRowDTO {
    private LocalDate date;
    /** "COMPRA" o "PAGO". */
    private String type;
    private String description;
    private Long purchaseId;
    private BigDecimal charge;
    private BigDecimal payment;
    private BigDecimal balance;
}
