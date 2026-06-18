package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
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

    // Los parámetros del perfil con su orden. Cada fila guarda display_order en
    // la tabla de unión; @OrderBy hace que se lean ya ordenados, que es el orden
    // que respeta el reporte al imprimir.
    @OneToMany(mappedBy = "testConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder")
    private List<TestConfigParameter> configParameters = new ArrayList<>();

    private boolean active;
}
