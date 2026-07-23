package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.List;

/**
 * Remisión de exámenes de una orden a otro laboratorio: porque el laboratorio
 * propio no realiza la prueba, o porque un resultado alterado necesita
 * verificación externa. Queda como registro (historial + reimpresión de la
 * boleta de remisión).
 */
@Entity
@Table(name = "referral")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private LabOrder order;

    /** Nombre del laboratorio de destino (texto libre con memoria/autocompletado). */
    @Column(nullable = false)
    private String destinationLabName;

    /** Motivo de la remisión (ej. verificar resultado alterado, prueba no disponible). */
    @Column(length = 2000)
    private String reason;

    private Instant referredAt;

    /** Usuario que generó la remisión (capturado del contexto de seguridad). */
    private String createdByUsername;

    /**
     * Cómo se salda el costo con el laboratorio de destino. {@code null} = queda
     * por pagar (se acredita Cuentas por pagar); si trae método, se pagó al
     * momento (se acredita Caja o Bancos según el método).
     */
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @OneToMany(mappedBy = "referral", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ReferralItem> items;
}
