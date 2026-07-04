package marroquinsoftware.labflowapi.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Genera y hashea los tokens de invitación. El token en claro solo viaja en el
 * enlace del correo; en la BD se guarda su hash SHA-256 (búsqueda determinística
 * por hash, a diferencia de BCrypt que es salado y no permite lookup).
 */
public final class InvitationTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private InvitationTokens() {
    }

    /** Token aleatorio de 256 bits, URL-safe, para poner en el enlace. */
    public static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hash SHA-256 (hex) del token, que es lo que se guarda y se compara. */
    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
