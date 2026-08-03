package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Un correo puede tener varias filas (una por laboratorio), así que la
    // búsqueda por username ya no es única.

    /** Membresía concreta del usuario en un laboratorio (carga por request). */
    Optional<User> findByUsernameAndLaboratoryId(String username, Long laboratoryId);

    /** ¿El correo ya es miembro de ESTE laboratorio? (unicidad por-lab en invitaciones). */
    boolean existsByUsernameAndLaboratoryId(String username, Long laboratoryId);

    /** ¿El correo existe en cualquier laboratorio? (registro público de identidad nueva). */
    boolean existsByUsername(String username);

    /**
     * Cualquier fila del correo. Sirve para reusar el hash al invitar/crear un lab
     * para un correo ya existente.
     */
    Optional<User> findFirstByUsernameOrderById(String username);

    /**
     * Primera membresía HABILITADA del correo. Se prefiere para el chequeo de
     * contraseña del login: todas las filas activas comparten el hash, y así se
     * ignoran las invitaciones pendientes (deshabilitadas, con hash provisional).
     */
    Optional<User> findFirstByUsernameAndEnabledTrueOrderById(String username);

    /** Todas las membresías del correo (para armar el selector de laboratorio al iniciar sesión). */
    List<User> findByUsernameOrderById(String username);

    // app_user no usa @TenantId (el login busca por username),
    // así que el laboratorio se filtra explícitamente.
    List<User> findByLaboratoryIdOrderByUsername(Long laboratoryId);

    /**
     * Nombre de la persona por (correo, laboratorio), para mostrar en documentos en
     * vez del correo. Proyección escalar: no hidrata el User ni su AppRole. Devuelve
     * vacío si no hay fila o si el nombre es nulo (el llamador cae al username).
     */
    @Query("select u.name from User u where u.username = :username and u.laboratory.id = :laboratoryId")
    Optional<String> findNameByUsernameAndLaboratoryId(@Param("username") String username,
                                                       @Param("laboratoryId") Long laboratoryId);
    long countByAppRole_Id(Long roleId);

    // Búsqueda global por token de invitación (endpoint público sin tenant).
    Optional<User> findByInvitationTokenHash(String invitationTokenHash);

    // Solo el id del laboratorio, sin hidratar el AppRole (que es @TenantId y
    // fallaría bajo el tenant vacío del endpoint público). Se usa para fijar el
    // TenantContext antes de cargar el usuario completo.
    @Query("select u.laboratory.id from User u where u.invitationTokenHash = :hash")
    Optional<Long> findLaboratoryIdByInvitationTokenHash(String hash);

    // --- Restablecimiento de contraseña ---
    // Todo el flujo público (validar token, fijar contraseña) usa proyecciones y
    // updates masivos por username: nunca hidrata la entidad User ni su laboratorio,
    // así el endpoint público funciona sin tenant y el cambio se propaga a TODAS las
    // filas del correo (invariante de contraseña compartida entre laboratorios).

    /** ¿El correo tiene una cuenta activa (con contraseña)? Sin hidratar entidades. */
    boolean existsByUsernameAndEnabledTrue(String username);

    /** Vista mínima del token de reset: username + expiración, sin cargar el User ni su AppRole. */
    interface ResetTokenView {
        String getUsername();
        Instant getExpiresAt();
    }

    @Query("select u.username as username, u.resetExpiresAt as expiresAt from User u where u.resetTokenHash = :hash")
    Optional<ResetTokenView> findResetByTokenHash(@Param("hash") String hash);

    /** Fija el token de reset en TODAS las filas del correo. */
    @Modifying
    @Query("update User u set u.resetTokenHash = :hash, u.resetExpiresAt = :exp where u.username = :username")
    int setResetTokenByUsername(@Param("username") String username,
                                @Param("hash") String hash,
                                @Param("exp") Instant exp);

    /** Fija la nueva contraseña en TODAS las filas del correo y limpia el token de reset. */
    @Modifying
    @Query("update User u set u.password = :hash, u.resetTokenHash = null, u.resetExpiresAt = null where u.username = :username")
    int updatePasswordByUsername(@Param("username") String username, @Param("hash") String hash);
}
