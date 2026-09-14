package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "test_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "test_id", nullable = false)
    private LabTest test;

    private Integer runNumber;
    private Instant performedAt;
    private Boolean isVerified;

    // Se recorren los resultados de muchas corridas a la vez (detalle de orden,
    // historial del paciente); sin el @BatchSize sería una consulta por corrida al
    // inicializar esta colección lazy (N+1).
    @OneToMany(mappedBy = "testRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<TestResult> results;

    // Fotos/escaneos del reporte del equipo, para exámenes cuyo perfil permite
    // adjuntos (TestConfig.allowResultAttachments). Independiente de results:
    // una corrida puede traer resultados, adjuntos, o ambos.
    @OneToMany(mappedBy = "testRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<TestRunAttachment> attachments;
}
