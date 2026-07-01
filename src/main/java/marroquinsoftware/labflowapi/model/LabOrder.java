package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "lab_orders", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_order_number_per_lab",
        columnNames = {"laboratory_id", "order_number"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Correlativo visible de la orden, único por laboratorio (folio 1, 2, 3…).
     * A diferencia del {@code id}, se asigna desde un contador transaccional
     * ({@link marroquinsoftware.labflowapi.model.LabOrderCounter}) para que no
     * queden huecos cuando una creación falla y hace rollback.
     */
    @Column(name = "order_number")
    private Long orderNumber;

    @ManyToOne
    @JoinColumn(name = "laboratory_id")
    private Laboratory laboratory;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String notes;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<LabTest> tests;
}
