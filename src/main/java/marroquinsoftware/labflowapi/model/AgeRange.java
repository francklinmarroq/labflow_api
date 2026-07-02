package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_age_range_name_per_lab", columnNames = {"laboratory_id", "name"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgeRange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @NotBlank
    private String name;

    private Integer minAgeDays; // null = no lower bound
    private Integer maxAgeDays; // null = no upper bound
}
