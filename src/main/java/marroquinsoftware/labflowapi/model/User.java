package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Usuario de la aplicación. Sustituye a la tabla {@code users} de
 * JdbcUserDetailsManager para poder ligar cada usuario a su laboratorio (tenant).
 * El {@code username} es el correo.
 */
@Entity
@Table(name = "app_user")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Email
    @Column(unique = true, nullable = false, length = 255)
    private String username;

    @NotBlank
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Laboratorio (tenant) al que pertenece el usuario. Varios usuarios por laboratorio. */
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "laboratory_id", nullable = false)
    private Laboratory laboratory;

    /**
     * Rol configurable que otorga los permisos (solo aplica a STAFF; el OWNER
     * siempre tiene todos los permisos y no usa rol).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "app_role_id")
    private AppRole appRole;
}
