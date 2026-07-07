package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.ReferenceContextKind;
import marroquinsoftware.labflowapi.model.Sex;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceRangeDTO {
    private Long id;

    @NotNull
    private Long parameterId;

    private Sex sex;
    private Long ageRangeId; // null = applies to all ages
    private BigDecimal lowerLimit;
    private boolean lowerExclusive;
    private BigDecimal upperLimit;
    private boolean upperExclusive;
    private BigDecimal criticalLow;
    private BigDecimal criticalHigh;
    private String interpretationText;

    private ReferenceContextKind contextKind = ReferenceContextKind.NONE;
    private String contextLabel;
    private Integer contextMin;
    private Integer contextMax;
}
