package marroquinsoftware.labflowapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Envío de correos por el API HTTP de Resend (https://resend.com), no por SMTP:
 * las plataformas tipo Railway bloquean los puertos SMTP salientes (25/465/587),
 * pero el API va por HTTPS (443), que nunca se bloquea.
 */
@Service
public class EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);
    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final RestClient restClient = RestClient.create();

    @Value("${app.mailFrom}")
    private String from;

    @Value("${app.resend.apiKey:}")
    private String resendApiKey;

    /**
     * Envía la invitación de forma asíncrona: si el API falla o tarda, no tumba
     * la creación del usuario (el owner puede reenviar la invitación después).
     */
    @Async
    public void sendInvitation(String to, String labName, String roleName, String acceptUrl) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            LOGGER.warn("RESEND_API_KEY vacío. No se envió la invitación a {}. "
                    + "Enlace de aceptación: {}", to, acceptUrl);
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "from", from,
                    "to", List.of(to),
                    "subject", "Invitación para unirse a " + labName + " en LabFlow",
                    "html", buildHtml(labName, roleName, acceptUrl)
            );
            restClient.post()
                    .uri(RESEND_ENDPOINT)
                    .header("Authorization", "Bearer " + resendApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            LOGGER.info("Invitación enviada a {}", to);
        } catch (RestClientResponseException e) {
            // El cuerpo de la respuesta trae el motivo real de Resend
            // (ej. dominio no verificado, remitente inválido).
            LOGGER.error("Resend rechazó la invitación a {}: {} {}",
                    to, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            LOGGER.error("No se pudo enviar la invitación a {}: {}", to, e.getMessage());
        }
    }

    private String buildHtml(String labName, String roleName, String acceptUrl) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; color: #1e293b;">
                  <h2 style="color: #0f766e;">Te invitaron a LabFlow</h2>
                  <p>Fuiste invitado a unirte a <strong>%s</strong> con el rol de <strong>%s</strong>.</p>
                  <p>Haz clic en el botón para crear tu contraseña y activar tu cuenta:</p>
                  <p style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background: #0f766e; color: #fff; padding: 12px 24px;
                       border-radius: 8px; text-decoration: none; font-weight: bold;">
                      Aceptar invitación
                    </a>
                  </p>
                  <p style="color: #64748b; font-size: 13px;">
                    Si el botón no funciona, copia y pega este enlace en tu navegador:<br>
                    <a href="%s">%s</a>
                  </p>
                  <p style="color: #94a3b8; font-size: 12px;">
                    Si no esperabas esta invitación, puedes ignorar este correo.
                  </p>
                </div>
                """.formatted(labName, roleName, acceptUrl, acceptUrl, acceptUrl);
    }
}
