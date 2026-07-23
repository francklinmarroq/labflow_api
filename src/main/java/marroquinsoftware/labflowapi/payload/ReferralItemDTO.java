package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferralItemDTO {
    private Long id;
    private Long labTestId;
    private String testName;
    /** Costo del examen remitido (lo que cobra el laboratorio de destino). */
    private BigDecimal cost;
}
