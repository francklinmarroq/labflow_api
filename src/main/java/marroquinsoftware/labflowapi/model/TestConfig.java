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
public class TestConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String testTitle;

    @NotBlank
    @Column(unique = true)
    private String testName;

    @ManyToMany
    @JoinTable(
            name = "test_config_parameters",
            joinColumns = @JoinColumn(name = "test_config_id"),
            inverseJoinColumns = @JoinColumn(name = "parameter_id")
    )
    private List<Parameter> parameters;

    private boolean active;
}
