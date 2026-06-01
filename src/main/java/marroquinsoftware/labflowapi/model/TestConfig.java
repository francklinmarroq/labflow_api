package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "test_id", nullable = false)
    private Test test;

    @NotBlank
    @Column(unique = true)
    private String name;

    @ManyToMany
    @JoinTable(
            name = "test_config_parameters",
            joinColumns = @JoinColumn(name = "test_config_id"),
            inverseJoinColumns = @JoinColumn(name = "parameter_id")
    )
    private Set<Parameter> parameters;

    private boolean active;
}
