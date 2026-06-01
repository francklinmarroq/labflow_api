package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    private Integer ageInDays;

    @Enumerated(EnumType.STRING)
    private Sex sex;

    @Column(unique = true)
    private String nationalIdNumber;

    @Column(unique = true)
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
