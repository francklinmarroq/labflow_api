package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contador del correlativo de partidas del diario por laboratorio. Funciona
 * igual que {@link QuoteCounter}: una fila por laboratorio, leída con bloqueo
 * pesimista dentro de la transacción que crea la partida.
 */
@Entity
@Table(name = "journal_entry_counters")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryCounter {

    @Id
    @Column(name = "laboratory_id")
    private Long laboratoryId;

    @Column(name = "next_number", nullable = false)
    private Long nextNumber;
}
