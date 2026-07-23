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
import java.time.LocalDate;
import java.util.List;

/**
 * Partida del libro diario. Puede venir de un evento del sistema (factura,
 * pago, gasto o sus anulaciones, con {@code sourceType}/{@code sourceId}
 * apuntando al documento origen) o ser una partida manual del usuario. Sus
 * líneas siempre cuadran: la suma de débitos es igual a la de créditos, y eso
 * lo garantiza el servicio antes de guardar. Las partidas no se editan ni se
 * borran; un error se corrige con un contra-asiento.
 */
@Entity
@Table(name = "journal_entries", uniqueConstraints = @UniqueConstraint(
        name = "uk_journal_entry_number_per_lab",
        columnNames = {"laboratory_id", "entry_number"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Correlativo visible por laboratorio, asignado desde {@link JournalEntryCounter}. */
    @Column(name = "entry_number")
    private Long entryNumber;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JournalSourceType sourceType;

    /** Id del documento origen (factura, pago o gasto); null en partidas manuales. */
    private Long sourceId;

    private Instant createdAt;

    private String createdByUsername;

    // El diario se muestra con sus líneas; sin el @BatchSize sería una consulta
    // por partida al recorrer la página.
    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<JournalLine> lines;
}
