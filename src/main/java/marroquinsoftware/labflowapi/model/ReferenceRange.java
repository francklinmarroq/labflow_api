package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceRange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "parameter_id", nullable = false)
    private Parameter parameter;

    @Enumerated(EnumType.STRING)
    private Sex sex; // null = applies to both sexes

    @ManyToOne
    @JoinColumn(name = "age_range_id")
    private AgeRange ageRange; // null = applies to all ages

    private BigDecimal lowerLimit;
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean lowerExclusive;
    private BigDecimal upperLimit;
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean upperExclusive;
    private BigDecimal criticalLow;
    private BigDecimal criticalHigh;
    private String interpretationText;
}
