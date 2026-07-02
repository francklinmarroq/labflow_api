package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

@Entity
@Table(name = "tests", uniqueConstraints = @UniqueConstraint(
        name = "uk_test_name_per_lab", columnNames = {"laboratory_id", "name"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Test {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @NotBlank
    private String name;

    private BigDecimal price;

    private BigDecimal cost;

    @Enumerated(EnumType.STRING)
    private TestArea area;
}
