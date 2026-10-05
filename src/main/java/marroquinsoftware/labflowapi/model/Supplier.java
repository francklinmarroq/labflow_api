package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.TenantId;

/**
 * Proveedor del laboratorio: a quién se le compra. Se desactiva en vez de
 * borrarse, porque sus compras y pagos siguen apuntándolo. El RTN es opcional
 * (hay proveedores informales), pero si se indica es único por laboratorio; eso
 * lo comprueba el servicio para poder nombrar en el mensaje a quién pertenece.
 */
@Entity
@Table(name = "suppliers", indexes = @Index(name = "ix_suppliers_rtn", columnList = "laboratory_id, rtn"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @Column(nullable = false)
    private String name;

    private String rtn;
    private String phone;
    private String email;
    private String address;

    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean active = true;
}
