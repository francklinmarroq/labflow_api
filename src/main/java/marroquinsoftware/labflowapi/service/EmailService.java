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

    /**
     * Plantilla del correo de invitación. Se usa una estructura basada en tablas
     * con estilos en línea (lo único que renderizan de forma consistente los
     * clientes de correo como Gmail y Outlook) y la paleta de LabFlow (índigo
     * {@code #4f46e5} sobre grises slate).
     */
    private String buildHtml(String labName, String roleName, String acceptUrl) {
        return EMAIL_TEMPLATE
                .replace("{{labName}}", escapeHtml(labName))
                .replace("{{roleName}}", escapeHtml(roleName))
                .replace("{{acceptUrl}}", acceptUrl);
    }

    /** Escapa el texto dinámico para que un nombre con `<`, `&`, etc. no rompa el HTML. */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static final String EMAIL_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <meta name="color-scheme" content="light">
            </head>
            <body style="margin:0; padding:0; background-color:#f1f5f9;">
              <!-- Texto de vista previa (oculto) -->
              <div style="display:none; max-height:0; overflow:hidden; opacity:0;">
                Te invitaron a unirte a {{labName}} en LabFlow. Crea tu contraseña para activar tu cuenta.
              </div>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                     style="background-color:#f1f5f9; padding:32px 12px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="480" cellpadding="0" cellspacing="0"
                           style="max-width:480px; width:100%; background-color:#ffffff; border-radius:16px;
                                  overflow:hidden; box-shadow:0 1px 3px rgba(15,23,42,0.08);
                                  font-family:'Segoe UI', Arial, sans-serif;">
                      <!-- Encabezado de marca -->
                      <tr>
                        <td style="background-color:#4f46e5; padding:24px 32px;">
                          <span style="font-size:20px; font-weight:bold; color:#ffffff; letter-spacing:0.3px;">
                            &#9889; LabFlow
                          </span>
                        </td>
                      </tr>
                      <!-- Cuerpo -->
                      <tr>
                        <td style="padding:32px;">
                          <h1 style="margin:0 0 8px; font-size:22px; color:#1e293b;">Te damos la bienvenida</h1>
                          <p style="margin:0 0 20px; font-size:15px; line-height:1.6; color:#475569;">
                            Fuiste invitado a unirte a
                            <strong style="color:#1e293b;">{{labName}}</strong> en LabFlow.
                          </p>
                          <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 24px;">
                            <tr>
                              <td style="background-color:#eef2ff; border-radius:9999px; padding:6px 14px;
                                         font-size:13px; font-weight:600; color:#4338ca;">
                                Rol asignado: {{roleName}}
                              </td>
                            </tr>
                          </table>
                          <p style="margin:0 0 28px; font-size:15px; line-height:1.6; color:#475569;">
                            Para activar tu cuenta, crea tu contraseña con el siguiente botón:
                          </p>
                          <!-- Botón -->
                          <table role="presentation" cellpadding="0" cellspacing="0" align="center" style="margin:0 auto;">
                            <tr>
                              <td align="center" bgcolor="#4f46e5" style="border-radius:8px;">
                                <a href="{{acceptUrl}}"
                                   style="display:inline-block; padding:14px 32px; font-size:16px; font-weight:bold;
                                          color:#ffffff; text-decoration:none; border-radius:8px;">
                                  Activar mi cuenta
                                </a>
                              </td>
                            </tr>
                          </table>
                          <p style="margin:28px 0 0; font-size:13px; line-height:1.6; color:#64748b;">
                            Si el botón no funciona, copia y pega este enlace en tu navegador:
                          </p>
                          <p style="margin:4px 0 0; font-size:13px; word-break:break-all;">
                            <a href="{{acceptUrl}}" style="color:#4f46e5;">{{acceptUrl}}</a>
                          </p>
                        </td>
                      </tr>
                      <!-- Pie -->
                      <tr>
                        <td style="padding:20px 32px; background-color:#f8fafc; border-top:1px solid #e2e8f0;">
                          <p style="margin:0; font-size:12px; line-height:1.6; color:#94a3b8;">
                            Por seguridad, este enlace caduca. Si ya expiró, pide al administrador de tu
                            laboratorio que te reenvíe la invitación. Si no esperabas este correo, puedes ignorarlo.
                          </p>
                        </td>
                      </tr>
                    </table>
                    <p style="margin:16px 0 0; font-size:12px; color:#94a3b8; font-family:'Segoe UI', Arial, sans-serif;">
                      LabFlow &middot; Gestión de laboratorio clínico
                    </p>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;
}
