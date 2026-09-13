package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import marroquinsoftware.labflowapi.controller.v1.AuthController;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.Role;
import marroquinsoftware.labflowapi.model.User;
import marroquinsoftware.labflowapi.payload.ReleaseNotesSeenRequest;
import marroquinsoftware.labflowapi.payload.UserInfoResponse;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.UserRepository;
import marroquinsoftware.labflowapi.security.AppUserDetails;
import marroquinsoftware.labflowapi.service.ReleaseNotesService;
import marroquinsoftware.labflowapi.service.ReleaseNotesServiceImp;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import marroquinsoftware.labflowapi.tenant.TenantIdentifierResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Marcador de novedades por usuario: la API recuerda cuál fue el último anuncio que
 * se le mostró a cada quien, lo devuelve tal cual en {@code /auth/me}, y no deja que
 * una marca vacía lo borre ni que la marca de uno toque la de otro. La API no
 * interpreta la versión: guarda la cadena que le manden.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({ReleaseNotesServiceImp.class, TenantIdentifierResolver.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class ReleaseNotesSeenTest {

    @Autowired ReleaseNotesService releaseNotesService;
    @Autowired UserRepository userRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @PersistenceContext EntityManager entityManager;

    private Long laboratoryId;
    private Long otherLaboratoryId;

    private static final String ANA = "ana@labflow.hn";
    private static final String BETO = "beto@labflow.hn";

    @BeforeEach
    void setUp() {
        laboratoryId = newLaboratory("Laboratorio de Prueba");
        otherLaboratoryId = newLaboratory("Otro Laboratorio");
        TenantContext.setLaboratoryId(laboratoryId);
        newUser(ANA, laboratoryId);
        newUser(BETO, laboratoryId);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    private Long newLaboratory(String name) {
        Laboratory laboratory = new Laboratory();
        laboratory.setName(name);
        return laboratoryRepository.save(laboratory).getId();
    }

    /** Usuario STAFF sin rol configurable: autenticado y sin ningún permiso. */
    private User newUser(String username, Long labId) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("no-importa");
        user.setEnabled(true);
        user.setRole(Role.STAFF);
        user.setLaboratory(laboratoryRepository.findById(labId).orElseThrow());
        return userRepository.save(user);
    }

    /** Vacía el contexto de persistencia para que la siguiente lectura venga de la base. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private String storedVersionOf(String username, Long labId) {
        return userRepository.findByUsernameAndLaboratoryId(username, labId)
                .orElseThrow()
                .getLastSeenReleaseVersion();
    }

    // --- Lectura de la identidad -------------------------------------------------

    @Test
    void aUserWhoWasMarkedReportsExactlyThatVersion() {
        releaseNotesService.markSeen(ANA, laboratoryId, "1.8.0");
        flushAndClear();

        assertEquals("1.8.0", storedVersionOf(ANA, laboratoryId));
    }

    // Quien nunca fue marcado lee ausente. Sustituirle una versión por defecto lo
    // dejaría sin ver el primer anuncio, que es justo para quien existe la función.
    @Test
    void aUserWhoWasNeverMarkedReportsAbsentWithNoDefault() {
        assertNull(storedVersionOf(ANA, laboratoryId), "nunca marcado debe leer null");
        assertNull(controller().me(principalFor(ANA, laboratoryId)).getBody().getLastSeenReleaseVersion(),
                "y /auth/me no puede inventarle una versión");
    }

    // La API no entiende de versiones: guarda y devuelve la cadena tal cual, sea cual
    // sea su forma. Si algún día el cliente cambia de esquema, no hay nada que migrar.
    @Test
    void theApiDoesNotInterpretTheVersion() {
        String extraño = "temporada-2026/otoño (beta) ✦";
        releaseNotesService.markSeen(ANA, laboratoryId, extraño);
        flushAndClear();

        assertEquals(extraño, storedVersionOf(ANA, laboratoryId));
    }

    // El marcador vive en la base, no en la sesión: sobrevive a que se limpie el
    // contexto de persistencia, que es lo más parecido acá a volver a entrar.
    @Test
    void theMarkSurvivesANewSession() {
        releaseNotesService.markSeen(ANA, laboratoryId, "1.8.0");
        flushAndClear();
        entityManager.clear();

        assertEquals("1.8.0", storedVersionOf(ANA, laboratoryId));
    }

    // --- Aislamiento -------------------------------------------------------------

    // Marcar lo de una persona no puede tocar lo de otra, ni siquiera en el mismo
    // laboratorio: el marcador es de quien llama y de nadie más.
    @Test
    void markingOneUserLeavesEveryOtherUserUntouched() {
        releaseNotesService.markSeen(ANA, laboratoryId, "1.8.0");
        flushAndClear();

        assertEquals("1.8.0", storedVersionOf(ANA, laboratoryId));
        assertNull(storedVersionOf(BETO, laboratoryId),
                "el compañero de laboratorio queda como estaba");
    }

    // El mismo correo en otro laboratorio es otra fila y otro marcador (por membresía,
    // ver design.md): marcar en uno no marca en el otro.
    @Test
    void theMarkerIsPerMembershipNotPerEmail() {
        newUser(ANA, otherLaboratoryId);
        flushAndClear();

        releaseNotesService.markSeen(ANA, laboratoryId, "1.8.0");
        flushAndClear();

        assertEquals("1.8.0", storedVersionOf(ANA, laboratoryId));
        assertNull(storedVersionOf(ANA, otherLaboratoryId),
                "la membresía en el otro laboratorio no se toca");
    }

    // --- El marcador no se puede limpiar -----------------------------------------

    @Test
    void anEmptyVersionCannotClearTheMarker() {
        releaseNotesService.markSeen(ANA, laboratoryId, "1.8.0");
        flushAndClear();

        releaseNotesService.markSeen(ANA, laboratoryId, "");
        releaseNotesService.markSeen(ANA, laboratoryId, "   ");
        releaseNotesService.markSeen(ANA, laboratoryId, null);
        flushAndClear();

        assertEquals("1.8.0", storedVersionOf(ANA, laboratoryId),
                "una marca vacía no devuelve al usuario a 'no ha visto nada'");
    }

    @Test
    void reMarkingTheSameVersionSucceedsAndChangesNothing() {
        releaseNotesService.markSeen(ANA, laboratoryId, "1.8.0");
        flushAndClear();
        assertDoesNotThrow(() -> releaseNotesService.markSeen(ANA, laboratoryId, "1.8.0"));
        flushAndClear();

        assertEquals("1.8.0", storedVersionOf(ANA, laboratoryId));
    }

    // Estar autenticado basta: un STAFF sin rol configurable no tiene ningún permiso
    // y aun así puede marcar lo suyo. Ningún permiso nuevo gatea esta operación.
    @Test
    void aUserWithNoPermissionsCanMarkItsOwnVersion() {
        AppUserDetails principal = principalFor(ANA, laboratoryId);
        assertTrue(principal.getPermissionNames().isEmpty(), "el usuario de prueba no tiene permisos");

        assertEquals(204, controller().markReleaseNotesSeen(
                principal, new ReleaseNotesSeenRequest("1.8.0")).getStatusCode().value());
        flushAndClear();

        assertEquals("1.8.0", storedVersionOf(ANA, laboratoryId));
    }

    // --- /auth/me ----------------------------------------------------------------

    // Marcar y volver a leer la identidad en la MISMA sesión tiene que reportar el
    // valor nuevo: si el marcador viajara en el token, esto seguiría devolviendo el
    // viejo hasta el siguiente login, y la persona volvería a ver el anuncio.
    @Test
    void meReportsTheMarkedVersionWithoutANewSession() {
        AppUserDetails principal = principalFor(ANA, laboratoryId);
        assertNull(controller().me(principal).getBody().getLastSeenReleaseVersion());

        controller().markReleaseNotesSeen(principal, new ReleaseNotesSeenRequest("1.8.0"));
        flushAndClear();

        assertEquals("1.8.0", controller().me(principal).getBody().getLastSeenReleaseVersion(),
                "me() lee de la base, no del principal ni del token");
    }

    // El constructor de UserInfoResponse es posicional y casi todo son String: si
    // alguien reordena sus argumentos, nada falla al compilar y el usuario ve el
    // laboratorio donde va su rol. Por eso se afirma sobre los VALORES de cada campo
    // y no solo sobre su presencia.
    @Test
    void meDoesNotShiftTheFieldsOfThePositionalConstructor() {
        releaseNotesService.markSeen(ANA, laboratoryId, "1.8.0");
        flushAndClear();

        UserInfoResponse body = controller().me(principalFor(ANA, laboratoryId)).getBody();

        assertNotNull(body);
        assertEquals(ANA, body.getUsername());
        assertNull(body.getName(), "el usuario de prueba no tiene nombre");
        assertEquals("STAFF", body.getRole());
        assertNull(body.getRoleName(), "sin rol configurable");
        assertEquals(List.of(), body.getPermissions());
        assertEquals(laboratoryId, body.getLaboratoryId());
        assertEquals("Laboratorio de Prueba", body.getLaboratoryName());
        assertEquals("1.8.0", body.getLastSeenReleaseVersion());
    }

    // --- Andamiaje ---------------------------------------------------------------

    private AppUserDetails principalFor(String username, Long labId) {
        return new AppUserDetails(userRepository.findByUsernameAndLaboratoryId(username, labId).orElseThrow());
    }

    /**
     * AuthController con solo lo que estas pruebas ejercitan. Se arma a mano porque
     * el controlador inyecta por campo y sus otras dependencias (login, invitaciones,
     * reset de contraseña) no participan en nada de esto.
     */
    private AuthController controller() {
        try {
            AuthController controller = new AuthController();
            inject(controller, "userRepository", userRepository);
            inject(controller, "releaseNotesService", releaseNotesService);
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void inject(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = AuthController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
