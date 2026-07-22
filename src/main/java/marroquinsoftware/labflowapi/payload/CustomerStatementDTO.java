package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Estado de cuenta de un paciente: facturas y pagos en orden cronológico. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerStatementDTO {
    private Long customerId;
    private String customerName;
    private List<CustomerStatementRowDTO> rows;
    private BigDecimal totalInvoiced;
    private BigDecimal totalPaid;
    private BigDecimal balance;
}
