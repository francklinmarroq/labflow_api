package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.User;
import marroquinsoftware.labflowapi.payload.InvitationInfoResponse;
import marroquinsoftware.labflowapi.repositories.UserRepository;
import marroquinsoftware.labflowapi.security.InvitationTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Aceptación de invitaciones. Endpoints públicos (sin tenant): el token se busca
 * globalmente por su hash, y el usuario ya trae su laboratorio y rol asociados.
 */
@Service
public class InvitationServiceImp implements InvitationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    @Transactional(readOnly = true)
    public InvitationInfoResponse getInvitation(String rawToken) {
        User user = loadValidInvitation(rawToken);
        return new InvitationInfoResponse(
                user.getUsername(),
                user.getLaboratory() != null ? user.getLaboratory().getName() : null,
                user.getAppRole() != null ? user.getAppRole().getName() : null
        );
    }

    @Override
    @Transactional
    public User acceptInvitation(String rawToken, String password) {
        User user = loadValidInvitation(rawToken);
        user.setPassword(bCryptPasswordEncoder.encode(password));
        user.setEnabled(true);
        user.setInvitationTokenHash(null);
        user.setInvitationExpiresAt(null);
        return userRepository.save(user);
    }

    private User loadValidInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new APIException("Invitación inválida.");
        }
        // El TenantContext ya lo fijó AuthTokenFilter a partir del token, así que
        // el AppRole (@TenantId) del invitado se resuelve sin problema.
        User user = userRepository.findByInvitationTokenHash(InvitationTokens.hash(rawToken))
                .orElseThrow(() -> new APIException("La invitación no existe o ya fue utilizada."));
        if (user.getInvitationExpiresAt() == null
                || user.getInvitationExpiresAt().isBefore(Instant.now())) {
            throw new APIException("La invitación expiró. Pida al administrador que la reenvíe.");
        }
        return user;
    }
}
