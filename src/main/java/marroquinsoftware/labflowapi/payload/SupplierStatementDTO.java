package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Estado de cuenta de un proveedor en un rango: saldo inicial, compras a
 * crédito y pagos vigentes en orden cronológico, y saldo final.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierStatementDTO {
    private Long supplierId;
    private String supplierName;
    private String supplierRtn;
    private LocalDate from;
    private LocalDate to;
    private BigDecimal openingBalance;
    private List<SupplierStatementRowDTO> rows;
    private BigDecimal totalCharges;
    private BigDecimal totalPayments;
    private BigDecimal closingBalance;
}
