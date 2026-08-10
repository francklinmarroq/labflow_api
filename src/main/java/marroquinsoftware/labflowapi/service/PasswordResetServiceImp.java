package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.payload.PasswordResetInfoResponse;
import marroquinsoftware.labflowapi.repositories.UserRepository;
import marroquinsoftware.labflowapi.repositories.UserRepository.ResetTokenView;
import marroquinsoftware.labflowapi.security.InvitationTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Restablecimiento de contraseña. Todo el flujo público trabaja con proyecciones
 * y updates masivos por {@code username}: nunca hidrata la entidad {@code User}
 * (funciona sin tenant en el endpoint público) y propaga la nueva contraseña a
 * TODAS las filas del correo (invariante de contraseña compartida entre labs).
 */
@Service
public class PasswordResetServiceImp implements PasswordResetService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private EmailService emailService;

    @Value("${app.frontendBaseUrl}")
    private String frontendBaseUrl;

    @Value("${app.passwordResetExpirationHours}")
    private long passwordResetExpirationHours;

    @Override
    @Transactional
    public void requestReset(String email) {
        // Anti-enumeración: solo se emite si hay una cuenta activa (con contraseña).
        // Las invitaciones pendientes no aplican (se activan por su propio enlace).
        // Pase lo que pase, el controlador responde 200 genérico.
        if (email == null || email.isBlank()) {
            return;
        }
        String normalized = email.trim();
        if (userRepository.existsByUsernameAndEnabledTrue(normalized)) {
            issueAndSend(normalized);
        }
    }

    @Override
    @Transactional
    public void issueAndSend(String username) {
        String rawToken = InvitationTokens.newRawToken();
        userRepository.setResetTokenByUsername(
                username,
                InvitationTokens.hash(rawToken),
                Instant.now().plus(passwordResetExpirationHours, ChronoUnit.HOURS));
        emailService.sendPasswordReset(username, buildResetUrl(rawToken));
    }

    @Override
    @Transactional(readOnly = true)
    public PasswordResetInfoResponse getReset(String rawToken) {
        ResetTokenView view = loadValidReset(rawToken);
        return new PasswordResetInfoResponse(view.getUsername());
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String password) {
        ResetTokenView view = loadValidReset(rawToken);
        if (password == null || password.length() < 8) {
            throw new APIException("La contraseña es obligatoria y debe tener al menos 8 caracteres.");
        }
        userRepository.updatePasswordByUsername(view.getUsername(), bCryptPasswordEncoder.encode(password));
    }

    private ResetTokenView loadValidReset(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new APIException("Enlace de restablecimiento inválido.");
        }
        ResetTokenView view = userRepository.findResetByTokenHash(InvitationTokens.hash(rawToken))
                .orElseThrow(() -> new APIException("El enlace no existe o ya fue utilizado."));
        if (view.getExpiresAt() == null || view.getExpiresAt().isBefore(Instant.now())) {
            throw new APIException("El enlace de restablecimiento expiró. Solicítelo de nuevo.");
        }
        return view;
    }

    private String buildResetUrl(String rawToken) {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        return base + "/restablecer-contrasena?token=" + rawToken;
    }
}
