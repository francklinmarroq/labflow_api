package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

/**
 * Línea de una partida del diario: una cuenta con su cargo (debe) o abono
 * (haber). Exactamente uno de los dos montos es mayor que cero; el otro queda
 * en 0.00. La validación vive en el servicio del diario.
 */
@Entity
@Table(name = "journal_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "entry_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private JournalEntry entry;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal debit;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal credit;

    /** Posición de la línea dentro de la partida, para imprimirla en orden. */
    @Column(name = "line_order")
    private Integer lineOrder;
}
