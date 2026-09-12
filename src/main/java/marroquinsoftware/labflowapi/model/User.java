package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Usuario de la aplicación. Sustituye a la tabla {@code users} de
 * JdbcUserDetailsManager para poder ligar cada usuario a su laboratorio (tenant).
 * El {@code username} es el correo.
 *
 * <p>Un mismo correo puede pertenecer a VARIOS laboratorios: hay una fila por
 * (correo, laboratorio), cada una con su rol y su membresía. Por eso el
 * {@code username} NO es único global, sino único por laboratorio
 * ({@code username + laboratory_id}). Todas las filas de un mismo correo
 * comparten el mismo hash de contraseña (ver InvitationServiceImp y
 * RegistrationService).
 */
@Entity
@Table(name = "app_user",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_app_user_username_per_lab",
                columnNames = {"username", "laboratory_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Email
    @Column(nullable = false, length = 255)
    private String username;

    /**
     * Nombre de la persona (para mostrar en la app y en los documentos, en vez del
     * correo). Nullable: los usuarios creados antes de esta columna no lo tienen y
     * caen al {@code username} al mostrarse. Como el correo puede tener varias filas
     * (una por laboratorio), cada fila lleva su propio nombre.
     */
    @Column(name = "name", length = 255)
    private String name;

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

    /**
     * Hash SHA-256 del token de invitación (se guarda hasheado, como la
     * contraseña, para que una fuga de BD no exponga invitaciones usables).
     * No nulo mientras la invitación esté pendiente; se limpia al aceptarla.
     */
    @Column(name = "invitation_token_hash", length = 64)
    private String invitationTokenHash;

    /** Fecha de expiración de la invitación pendiente. */
    @Column(name = "invitation_expires_at")
    private Instant invitationExpiresAt;

    /**
     * Hash SHA-256 del token de restablecimiento de contraseña (mismo esquema que
     * el de invitación: se guarda hasheado). No nulo mientras haya un reset
     * pendiente; se limpia al fijar la nueva contraseña. Como el correo puede tener
     * varias filas (una por laboratorio) y comparten hash, el token se fija en
     * todas ellas y se busca globalmente por su hash.
     */
    @Column(name = "reset_token_hash", length = 64)
    private String resetTokenHash;

    /** Fecha de expiración del token de restablecimiento pendiente. */
    @Column(name = "reset_expires_at")
    private Instant resetExpiresAt;

    /**
     * Última versión de novedades que ya se le anunció a este usuario. Null significa
     * "no ha visto nada": es el estado en el que arranca todo el mundo y no se rellena
     * con nada, porque inventarle un historial a quien no lo tiene lo dejaría sin ver
     * el primer anuncio.
     *
     * <p>La API guarda la cadena TAL CUAL y nunca la interpreta: no la ordena, no la
     * compara y no sabe qué trae una versión. Quién decide si toca anunciar es el
     * frontend, que es donde vive el contenido de las novedades.
     *
     * <p>Como el correo tiene una fila por laboratorio, el marcador es por membresía:
     * quien pertenece a tres laboratorios ve el anuncio una vez en cada uno. Es
     * deliberado (ver design.md del cambio track-release-notes-seen).
     */
    @Column(name = "last_seen_release_version", length = 255)
    private String lastSeenReleaseVersion;

    /** ¿El usuario fue invitado y aún no acepta (no tiene contraseña propia)? */
    @Transient
    public boolean isInvitationPending() {
        return invitationTokenHash != null;
    }
}
