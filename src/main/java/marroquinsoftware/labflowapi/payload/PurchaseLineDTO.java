package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.IsvRate;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseLineDTO {
    private Long id;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private IsvRate isvRate;
    private String isvRateLabel;
    private Long accountId;
    private String accountCode;
    private String accountName;
    private BigDecimal base;
    private BigDecimal isv;
}
