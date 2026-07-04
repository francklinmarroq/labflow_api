package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.AppRole;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.Role;
import marroquinsoftware.labflowapi.model.User;
import marroquinsoftware.labflowapi.payload.CreateUserRequest;
import marroquinsoftware.labflowapi.payload.UpdateUserRequest;
import marroquinsoftware.labflowapi.payload.UserAccountDTO;
import marroquinsoftware.labflowapi.repositories.AppRoleRepository;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.UserRepository;
import marroquinsoftware.labflowapi.security.InvitationTokens;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Gestión de los usuarios del laboratorio en sesión. Reglas de seguridad:
 * no se toca al usuario OWNER, nadie se deshabilita/elimina a sí mismo,
 * solo se crean usuarios STAFF y solo del laboratorio actual.
 *
 * <p>El alta es por invitación: se crea el usuario deshabilitado y sin
 * contraseña propia, y se le envía un enlace por correo para que la defina.
 */
@Service
public class UserAdminServiceImp implements UserAdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppRoleRepository appRoleRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private EmailService emailService;

    @Value("${app.frontendBaseUrl}")
    private String frontendBaseUrl;

    @Value("${app.invitationExpirationHours}")
    private long invitationExpirationHours;

    @Override
    public List<UserAccountDTO> getUsers() {
        return userRepository.findByLaboratoryIdOrderByUsername(TenantContext.getLaboratoryId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public UserAccountDTO createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new APIException("Ya existe un usuario con el correo: " + request.getUsername());
        }
        Laboratory laboratory = laboratoryRepository.getReferenceById(TenantContext.getLaboratoryId());
        User user = new User();
        user.setUsername(request.getUsername());
        // Contraseña inutilizable hasta que el usuario acepte y defina la suya;
        // además queda deshabilitado, así que no puede iniciar sesión.
        user.setPassword(bCryptPasswordEncoder.encode(UUID.randomUUID().toString()));
        user.setEnabled(false);
        user.setRole(Role.STAFF);
        user.setLaboratory(laboratory);
        user.setAppRole(loadRole(request.getRoleId()));
        String rawToken = issueInvitationToken(user);
        user = userRepository.save(user);
        emailService.sendInvitation(user.getUsername(), laboratory.getName(),
                user.getAppRole().getName(), buildAcceptUrl(rawToken));
        return toDto(user);
    }

    @Override
    @Transactional
    public UserAccountDTO resendInvite(Long userId) {
        User user = loadUser(userId);
        if (!user.isInvitationPending()) {
            throw new APIException("Este usuario ya aceptó su invitación.");
        }
        String rawToken = issueInvitationToken(user);
        user = userRepository.save(user);
        emailService.sendInvitation(user.getUsername(), user.getLaboratory().getName(),
                user.getAppRole() != null ? user.getAppRole().getName() : "", buildAcceptUrl(rawToken));
        return toDto(user);
    }

    @Override
    @Transactional
    public UserAccountDTO updateUser(UpdateUserRequest request, Long userId) {
        User user = loadUser(userId);
        if (user.getRole() == Role.OWNER) {
            throw new APIException("El usuario dueño del laboratorio no se puede modificar.");
        }
        if (Boolean.FALSE.equals(request.getEnabled()) && isCurrentUser(user)) {
            throw new APIException("No puede deshabilitar su propia cuenta.");
        }
        // No se habilita a un invitado desde aquí: se activa al aceptar por correo.
        if (request.getEnabled() != null && !user.isInvitationPending()) {
            user.setEnabled(request.getEnabled());
        }
        if (request.getRoleId() != null) {
            user.setAppRole(loadRole(request.getRoleId()));
        }
        return toDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserAccountDTO deleteUser(Long userId) {
        User user = loadUser(userId);
        if (user.getRole() == Role.OWNER) {
            throw new APIException("El usuario dueño del laboratorio no se puede eliminar.");
        }
        if (isCurrentUser(user)) {
            throw new APIException("No puede eliminar su propia cuenta.");
        }
        UserAccountDTO dto = toDto(user);
        userRepository.delete(user);
        return dto;
    }

    /** Genera un token nuevo, guarda su hash y expiración en el usuario y devuelve el token en claro. */
    private String issueInvitationToken(User user) {
        String rawToken = InvitationTokens.newRawToken();
        user.setInvitationTokenHash(InvitationTokens.hash(rawToken));
        user.setInvitationExpiresAt(Instant.now().plus(invitationExpirationHours, ChronoUnit.HOURS));
        return rawToken;
    }

    private String buildAcceptUrl(String rawToken) {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        return base + "/aceptar-invitacion?token=" + rawToken;
    }

    /** Carga un usuario verificando que pertenezca al laboratorio en sesión. */
    private User loadUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));
        if (user.getLaboratory() == null
                || !user.getLaboratory().getId().equals(TenantContext.getLaboratoryId())) {
            throw new ResourceNotFoundException("User", "userId", userId);
        }
        return user;
    }

    /** Carga un rol verificando que pertenezca al laboratorio en sesión. */
    private AppRole loadRole(Long roleId) {
        AppRole role = appRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "roleId", roleId));
        if (!role.getLaboratoryId().equals(TenantContext.getLaboratoryId())) {
            throw new ResourceNotFoundException("Role", "roleId", roleId);
        }
        return role;
    }

    private boolean isCurrentUser(User user) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && user.getUsername().equals(authentication.getName());
    }

    private UserAccountDTO toDto(User user) {
        return new UserAccountDTO(
                user.getId(),
                user.getUsername(),
                user.isEnabled(),
                user.getRole(),
                user.getAppRole() != null ? user.getAppRole().getId() : null,
                user.getAppRole() != null ? user.getAppRole().getName() : null,
                user.isInvitationPending()
        );
    }
}
