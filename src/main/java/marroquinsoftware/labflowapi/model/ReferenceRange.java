package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceRange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "parameter_id", nullable = false)
    private Parameter parameter;

    @Enumerated(EnumType.STRING)
    private Sex sex; // null = applies to both sexes

    @ManyToOne
    @JoinColumn(name = "age_range_id")
    private AgeRange ageRange; // null = applies to all ages (grupo de edad con nombre, reutilizable)

    // Límites de edad propios de esta fila, para tablas de valores "por edad" que
    // no reutilizan un grupo con nombre (ej. fosfatasa alcalina: recién nacido,
    // 1 mes, 1 año…). Si están presentes tienen prioridad sobre ageRange. Una
    // "edad específica" se expresa como escalón: solo minAgeDays ("desde"), y el
    // reporte elige la fila con el mayor "desde" ≤ la edad del paciente.
    private Integer minAgeDays;
    private Integer maxAgeDays;

    private BigDecimal lowerLimit;
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean lowerExclusive;
    private BigDecimal upperLimit;
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean upperExclusive;
    private BigDecimal criticalLow;
    private BigDecimal criticalHigh;
    private String interpretationText;

    // Contexto fisiológico del rango (fase del ciclo, gestación, menopausia).
    // NONE = rango común que aplica solo por sexo/edad. Cuando no es NONE, la
    // ventana contextMin/contextMax define en qué día del ciclo o semana de
    // gestación aplica, para que el reporte lo seleccione automáticamente.
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'NONE'")
    @Column(nullable = false)
    private ReferenceContextKind contextKind = ReferenceContextKind.NONE;
    private String contextLabel; // texto impreso: "Fase lútea", "Gestación 2.º trim.", "Hombres"
    private Integer contextMin;
    private Integer contextMax;
}
