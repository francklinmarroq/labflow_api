package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "test_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "test_run_id", nullable = false)
    private TestRun testRun;

    @ManyToOne
    @JoinColumn(name = "parameter_id", nullable = false)
    private Parameter parameter;

    @Column(name = "result_value")
    private String value;

    // Snapshot (JSON) de los rangos de referencia que aplicaban al paciente al
    // momento de reportar este resultado. Se congela aquí para que la reimpresión
    // muestre los valores con los que se reportó, aunque luego se editen los
    // rangos del catálogo. Null en resultados creados antes de existir el snapshot.
    @Column(name = "reference_ranges_snapshot", columnDefinition = "text")
    private String referenceRangesSnapshot;
}
