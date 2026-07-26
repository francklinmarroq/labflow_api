package marroquinsoftware.labflowapi.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtUtils.class);
    @Value("${spring.app.jwtSecret}")
    private String jwtSecret;

    @Value("${spring.app.jwtExpirationMs}")
    private int jwtExpirationMs;

    // El token de selección solo sirve para escoger laboratorio justo tras el
    // login; vive poco a propósito porque no autentica ninguna ruta de datos.
    private static final long SELECTION_TOKEN_EXPIRATION_MS = 5 * 60 * 1000L;

    public String getJwtFromHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        LOGGER.debug("Authorization Header: {}", bearerToken);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // This removes the Bearer prefix
        }
        return null;
    }

    public String generateTokenFromUsername(UserDetails userDetails) {
        String username = userDetails.getUsername();
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + jwtExpirationMs))
                .signWith(key())
                .compact();
    }

    public String generateToken(AppUserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("labId", userDetails.getLaboratoryId())
                .claim("role", userDetails.getAuthorities().stream().findFirst()
                        .map(a -> a.getAuthority()).orElse(null))
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + jwtExpirationMs))
                .signWith(key())
                .compact();
    }

    /**
     * Token corto que acredita que el usuario ya validó credenciales, para que
     * elija en cuál de sus laboratorios entrar. No lleva labId ni permisos y no
     * autentica ninguna ruta de datos (ver AuthTokenFilter); solo lo consume
     * POST /auth/login/select.
     */
    public String generateSelectionToken(String username) {
        return Jwts.builder()
                .subject(username)
                .claim("sel", true)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + SELECTION_TOKEN_EXPIRATION_MS))
                .signWith(key())
                .compact();
    }

    public String getUsernameFromJwtToken(String token) {
        return parseClaims(token).getSubject();
    }

    /** labId embebido en el token (fuente de verdad del tenant); null si es un token de selección. */
    public Long getLaboratoryIdFromJwtToken(String token) {
        Object value = parseClaims(token).get("labId");
        return value == null ? null : ((Number) value).longValue();
    }

    /** ¿Es un token de selección de laboratorio (no un JWT de sesión completo)? */
    public boolean isSelectionToken(String token) {
        return Boolean.TRUE.equals(parseClaims(token).get("sel"));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith((SecretKey) key()).build().parseSignedClaims(token).getPayload();
    }

    public Key key() {
        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(jwtSecret)
        );
    }

    public boolean validateJwtToken(String authToken) {
        try {
            System.out.println("Validate");
            Jwts.parser()
                    .verifyWith((SecretKey) key())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (MalformedJwtException e) {
            LOGGER.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            LOGGER.error("JWT token is exprired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            LOGGER.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            LOGGER.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

}
