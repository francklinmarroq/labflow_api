package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una compra vigente en el libro de compras, con su desglose fiscal. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseBookRowDTO {
    private Long purchaseId;
    private LocalDate purchaseDate;
    private String fiscalNumber;
    private String cai;
    private String supplierRtn;
    private String supplierName;
    private BigDecimal exemptBase;
    private BigDecimal taxedBase15;
    private BigDecimal taxedBase18;
    private BigDecimal isv15;
    private BigDecimal isv18;
    private BigDecimal total;
}
