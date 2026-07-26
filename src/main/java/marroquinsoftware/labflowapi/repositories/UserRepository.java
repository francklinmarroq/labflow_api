package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
    long countByAppRole_Id(Long roleId);

    // Búsqueda global por token de invitación (endpoint público sin tenant).
    Optional<User> findByInvitationTokenHash(String invitationTokenHash);

    // Solo el id del laboratorio, sin hidratar el AppRole (que es @TenantId y
    // fallaría bajo el tenant vacío del endpoint público). Se usa para fijar el
    // TenantContext antes de cargar el usuario completo.
    @Query("select u.laboratory.id from User u where u.invitationTokenHash = :hash")
    Optional<Long> findLaboratoryIdByInvitationTokenHash(String hash);
}
