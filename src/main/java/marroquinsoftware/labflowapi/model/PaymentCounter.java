package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contador del correlativo de recibos de pago por laboratorio. Funciona igual
 * que {@link QuoteCounter}: una fila por laboratorio, leída con bloqueo
 * pesimista dentro de la transacción que registra el pago.
 */
@Entity
@Table(name = "payment_counters")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCounter {

    @Id
    @Column(name = "laboratory_id")
    private Long laboratoryId;

    @Column(name = "next_number", nullable = false)
    private Long nextNumber;
}
