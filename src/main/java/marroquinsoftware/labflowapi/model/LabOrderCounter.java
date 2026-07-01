package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contador del correlativo de órdenes por laboratorio.
 *
 * <p>Existe una fila por laboratorio; {@code nextNumber} es el siguiente folio a
 * entregar. Se lee y actualiza dentro de la misma transacción que crea la orden
 * y con bloqueo pesimista, de modo que:
 * <ul>
 *   <li>dos creaciones concurrentes no obtienen el mismo folio, y</li>
 *   <li>si la creación de la orden falla, el incremento del contador también se
 *       revierte y el número no se pierde (no quedan huecos).</li>
 * </ul>
 */
@Entity
@Table(name = "lab_order_counters")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabOrderCounter {

    @Id
    @Column(name = "laboratory_id")
    private Long laboratoryId;

    @Column(name = "next_number", nullable = false)
    private Long nextNumber;
}
