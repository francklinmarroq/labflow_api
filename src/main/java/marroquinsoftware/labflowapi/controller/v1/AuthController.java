package marroquinsoftware.labflowapi.controller.v1;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.Role;
import marroquinsoftware.labflowapi.model.User;
import marroquinsoftware.labflowapi.payload.AcceptInvitationRequest;
import marroquinsoftware.labflowapi.payload.InvitationInfoResponse;
import marroquinsoftware.labflowapi.payload.JwtResponse;
import marroquinsoftware.labflowapi.payload.LabSelectionResponse;
import marroquinsoftware.labflowapi.payload.LabSummary;
import marroquinsoftware.labflowapi.payload.LaboratoryDTO;
import marroquinsoftware.labflowapi.payload.LoginRequest;
import marroquinsoftware.labflowapi.payload.LoginSelectRequest;
import marroquinsoftware.labflowapi.payload.RegisterRequest;
import marroquinsoftware.labflowapi.payload.UserInfoResponse;
import marroquinsoftware.labflowapi.repositories.UserRepository;
import marroquinsoftware.labflowapi.security.AppUserDetails;
import marroquinsoftware.labflowapi.security.JwtUtils;
import marroquinsoftware.labflowapi.service.InvitationService;
import marroquinsoftware.labflowapi.service.RegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private InvitationService invitationService;
    @Autowired
    private UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (DisabledException e) {
            return unauthorized("Su cuenta está deshabilitada. Contacte al administrador de su laboratorio.");
        } catch (LockedException e) {
            return unauthorized("Su cuenta está bloqueada. Contacte al administrador de su laboratorio.");
        } catch (AuthenticationException e) {
            return unauthorized("Correo o contraseña incorrectos.");
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
        AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();

        // Un mismo correo puede pertenecer a varios laboratorios. Si solo tiene uno,
        // se emite el JWT directamente (compatible con el flujo anterior). Si tiene
        // más, se devuelve un token de selección + la lista para que el usuario elija.
        List<User> memberships = activeMemberships(userDetails.getUsername());
        if (memberships.size() <= 1) {
            // Si la generación del token falla, la excepción sube al handler global,
            // que responde con mensaje en español y código de soporte.
            String jwtToken = jwtUtils.generateToken(userDetails);
            return new ResponseEntity<>(buildJwtResponse(jwtToken, userDetails), HttpStatus.OK);
        }
        LabSelectionResponse selection = new LabSelectionResponse(
                jwtUtils.generateSelectionToken(userDetails.getUsername()),
                memberships.stream().map(this::toLabSummary).toList());
        return new ResponseEntity<>(selection, HttpStatus.OK);
    }

    /**
     * Segundo paso del login cuando el correo tiene varios laboratorios: con el
     * token de selección (que acredita las credenciales ya validadas) el usuario
     * elige el laboratorio y recibe el JWT de sesión. Es público porque el token de
     * selección no autentica rutas normales; aquí se valida a mano.
     */
    @PostMapping("/login/select")
    public ResponseEntity<?> selectLaboratory(HttpServletRequest httpRequest,
                                              @Valid @RequestBody LoginSelectRequest request) {
        String token = jwtUtils.getJwtFromHeader(httpRequest);
        if (token == null || !jwtUtils.validateJwtToken(token) || !jwtUtils.isSelectionToken(token)) {
            return unauthorized("La selección de laboratorio expiró. Inicie sesión de nuevo.");
        }
        String username = jwtUtils.getUsernameFromJwtToken(token);
        User membership = userRepository.findByUsernameAndLaboratoryId(username, request.getLaboratoryId())
                .filter(u -> u.isEnabled() && !u.isInvitationPending())
                .orElse(null);
        if (membership == null) {
            return unauthorized("No tiene acceso a ese laboratorio.");
        }
        AppUserDetails userDetails = new AppUserDetails(membership);
        String jwtToken = jwtUtils.generateToken(userDetails);
        return new ResponseEntity<>(buildJwtResponse(jwtToken, userDetails), HttpStatus.OK);
    }

    /**
     * Crea un laboratorio adicional para el propietario en sesión (mismo correo) y
     * devuelve el JWT ya emitido para el nuevo laboratorio, de modo que quede
     * trabajando en él sin volver a iniciar sesión.
     */
    @PostMapping("/laboratories")
    public ResponseEntity<?> createLaboratory(@AuthenticationPrincipal AppUserDetails principal,
                                              @Valid @RequestBody LaboratoryDTO request) {
        if (principal.getRole() != Role.OWNER) {
            Map<String, Object> map = new HashMap<>();
            map.put("message", "Solo el propietario puede crear laboratorios.");
            map.put("status", false);
            return new ResponseEntity<>(map, HttpStatus.FORBIDDEN);
        }
        User user = registrationService.createAdditionalLaboratory(principal.getUsername(), request);
        AppUserDetails userDetails = new AppUserDetails(user);
        String jwtToken = jwtUtils.generateToken(userDetails);
        return new ResponseEntity<>(buildJwtResponse(jwtToken, userDetails), HttpStatus.CREATED);
    }

    /** Membresías activas del correo (habilitadas y ya aceptadas), ordenadas por id. */
    private List<User> activeMemberships(String username) {
        return userRepository.findByUsernameOrderById(username).stream()
                .filter(u -> u.isEnabled() && !u.isInvitationPending())
                .toList();
    }

    private LabSummary toLabSummary(User user) {
        return new LabSummary(
                user.getLaboratory().getId(),
                user.getLaboratory().getName(),
                user.getRole().name(),
                user.getAppRole() != null ? user.getAppRole().getName() : null);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            registrationService.register(request);
        } catch (APIException e) {
            Map<String, Object> map = new HashMap<>();
            map.put("message", e.getMessage());
            map.put("status", false);
            return new ResponseEntity<>(map, HttpStatus.CONFLICT);
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
        String jwtToken = jwtUtils.generateToken(userDetails);
        return new ResponseEntity<>(buildJwtResponse(jwtToken, userDetails), HttpStatus.CREATED);
    }

    private ResponseEntity<Object> unauthorized(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("message", message);
        map.put("status", false);
        return new ResponseEntity<>(map, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Identidad y permisos del usuario en sesión. El frontend lo llama al
     * cargar la app para refrescar permisos sin re-loguear.
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> me(@AuthenticationPrincipal AppUserDetails userDetails) {
        UserInfoResponse response = new UserInfoResponse(
                userDetails.getUsername(),
                userDetails.getRole().name(),
                userDetails.getRoleName(),
                userDetails.getPermissionNames(),
                userDetails.getLaboratoryId(),
                userDetails.getLaboratoryName()
        );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /** Datos de la invitación para la pantalla de aceptación (público). */
    @GetMapping("/invitation/{token}")
    public ResponseEntity<InvitationInfoResponse> getInvitation(@PathVariable String token) {
        return new ResponseEntity<>(invitationService.getInvitation(token), HttpStatus.OK);
    }

    /**
     * El usuario invitado define su contraseña y activa su cuenta (público).
     * Devuelve el JWT para dejarlo con la sesión iniciada.
     */
    @PostMapping("/invitation/{token}/accept")
    public ResponseEntity<?> acceptInvitation(@PathVariable String token,
                                              @Valid @RequestBody AcceptInvitationRequest request) {
        User user = invitationService.acceptInvitation(token, request.getPassword());
        AppUserDetails userDetails = new AppUserDetails(user);
        String jwtToken = jwtUtils.generateToken(userDetails);
        return new ResponseEntity<>(buildJwtResponse(jwtToken, userDetails), HttpStatus.OK);
    }

    private JwtResponse buildJwtResponse(String jwtToken, AppUserDetails userDetails) {
        return new JwtResponse(
                jwtToken,
                userDetails.getUsername(),
                userDetails.getRole().name(),
                userDetails.getRoleName(),
                userDetails.getPermissionNames(),
                userDetails.getLaboratoryId(),
                userDetails.getLaboratoryName()
        );
    }
}
