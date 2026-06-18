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

    @ManyToOne
    @JoinColumn(name = "test_id", nullable = false)
    private Test test;

    @NotBlank
    @Column(unique = true)
    private String name;

    // Los parámetros se guardan como lista ordenada. La columna display_order
    // en la tabla de unión preserva la posición de cada parámetro, y es la que
    // determina el orden en que se imprimen en el reporte.
    @ManyToMany
    @JoinTable(
            name = "test_config_parameters",
            joinColumns = @JoinColumn(name = "test_config_id"),
            inverseJoinColumns = @JoinColumn(name = "parameter_id")
    )
    @OrderColumn(name = "display_order")
    private List<Parameter> parameters;

    private boolean active;
}
