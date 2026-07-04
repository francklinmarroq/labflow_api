package marroquinsoftware.labflowapi.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Envío de correos por SMTP. El {@link JavaMailSender} se inyecta de forma
 * opcional (vía {@link ObjectProvider}) para que la app arranque aunque no haya
 * SMTP configurado en desarrollo; en ese caso el envío se omite con un aviso.
 */
@Service
public class EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mailFrom}")
    private String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    /**
     * Envía la invitación de forma asíncrona: si el SMTP falla o tarda, no tumba
     * la creación del usuario (el owner puede reenviar la invitación después).
     */
    @Async
    public void sendInvitation(String to, String labName, String roleName, String acceptUrl) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            LOGGER.warn("SMTP no configurado (spring.mail.host vacío). No se envió la invitación a {}. "
                    + "Enlace de aceptación: {}", to, acceptUrl);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Invitación para unirse a " + labName + " en LabFlow");
            helper.setText(buildHtml(labName, roleName, acceptUrl), true);
            mailSender.send(message);
            LOGGER.info("Invitación enviada a {}", to);
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
