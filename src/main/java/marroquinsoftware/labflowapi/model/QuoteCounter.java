package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contador del correlativo de cotizaciones por laboratorio. Funciona igual que
 * {@link LabOrderCounter}: una fila por laboratorio, leída con bloqueo pesimista
 * dentro de la transacción que crea la cotización.
 */
@Entity
@Table(name = "quote_counters")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuoteCounter {

    @Id
    @Column(name = "laboratory_id")
    private Long laboratoryId;

    @Column(name = "next_number", nullable = false)
    private Long nextNumber;
}
