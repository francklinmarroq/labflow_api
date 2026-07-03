package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

import java.util.List;

@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_national_id_per_lab", columnNames = {"laboratory_id", "national_id_number"}),
        @UniqueConstraint(name = "uk_customer_tax_number_per_lab", columnNames = {"laboratory_id", "tax_number"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @NotBlank
    private String name;

    private Integer ageInDays;

    @Enumerated(EnumType.STRING)
    private Sex sex;

    private String nationalIdNumber;

    private String taxNumber;

    private String phone;
    private String email;

    @ManyToMany
    @JoinTable(
            name = "customer_pathologies",
            joinColumns = @JoinColumn(name = "customer_id"),
            inverseJoinColumns = @JoinColumn(name = "pathology_id")
    )
    private List<Pathology> pathologies;
}
