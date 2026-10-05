package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Antigüedad de las cuentas por pagar a una fecha de corte. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayablesAgingDTO {
    private LocalDate date;
    private List<PayablesAgingRowDTO> rows;
    private BigDecimal totalDays0To30;
    private BigDecimal totalDays31To60;
    private BigDecimal totalDays61To90;
    private BigDecimal totalOver90;
    private BigDecimal total;
}
