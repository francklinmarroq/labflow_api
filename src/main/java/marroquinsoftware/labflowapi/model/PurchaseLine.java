package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

/**
 * Línea de una compra: qué se compró, a qué cuenta va y con qué tasa de ISV.
 * Lleva cantidad y precio unitario aunque hoy solo importe la base, para que un
 * futuro inventario pueda engancharse a la línea sin migrar documentos.
 */
@Entity
@Table(name = "purchase_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "purchase_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Purchase purchase;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IsvRate isvRate;

    /** Cuenta de GASTO o ACTIVO a la que se carga la base de la línea. */
    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /** cantidad × precio unitario, redondeado a 2 decimales. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal base;

    /** base × tasa, redondeado a 2 decimales. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal isv;

    @Column(name = "line_order")
    private Integer lineOrder;
}
