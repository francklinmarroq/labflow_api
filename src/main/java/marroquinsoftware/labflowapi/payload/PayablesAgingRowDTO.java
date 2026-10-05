package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Saldo pendiente de un proveedor repartido por días desde la fecha de compra. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayablesAgingRowDTO {
    private Long supplierId;
    private String supplierName;
    private String supplierRtn;
    private BigDecimal days0To30;
    private BigDecimal days31To60;
    private BigDecimal days61To90;
    private BigDecimal over90;
    private BigDecimal total;
}
