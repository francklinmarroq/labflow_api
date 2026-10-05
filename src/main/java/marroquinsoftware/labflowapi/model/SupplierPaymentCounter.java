package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Siguiente número de recibo de pago a proveedor por laboratorio. Se lee con
 * bloqueo de escritura para que dos pagos simultáneos no compartan número, igual
 * que {@link PaymentCounter} con los recibos de venta.
 */
@Entity
@Table(name = "supplier_payment_counters")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPaymentCounter {

    @Id
    @Column(name = "laboratory_id")
    private Long laboratoryId;

    @Column(name = "next_number", nullable = false)
    private Long nextNumber;
}
