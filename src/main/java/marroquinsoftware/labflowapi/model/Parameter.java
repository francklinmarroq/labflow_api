package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Parameter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name="unit_id")
    private Unit unit;

    @NotBlank
    private String name;

    @Enumerated(EnumType.STRING)
    private ParameterSection section;

    @Enumerated(EnumType.STRING)
    private ParameterValueType valueType;
}
