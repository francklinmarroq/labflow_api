package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.TenantId;

/**
 * Cuenta del catálogo contable del laboratorio. Las cuentas que los asientos
 * automáticos necesitan ubicar llevan una {@link SystemAccountKey}; el resto
 * son cuentas del usuario. Las cuentas nunca se borran: se desactivan (dejan
 * de aparecer en los selectores pero su historia en el diario queda intacta).
 */
@Entity
@Table(name = "accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_code_per_lab", columnNames = {"laboratory_id", "code"}),
        @UniqueConstraint(name = "uk_account_system_key_per_lab", columnNames = {"laboratory_id", "system_key"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    /** Código visible y ordenable (ej. "1101"). Único por laboratorio. */
    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    /** Clave de cuenta del sistema; null para cuentas creadas por el usuario. */
    @Enumerated(EnumType.STRING)
    @Column(name = "system_key")
    private SystemAccountKey systemKey;

    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean active = true;
}
