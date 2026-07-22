package marroquinsoftware.labflowapi.controller.v1;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Señal de vida pública y barata.
 *
 * La usa el cron del Worker para mantener despierto el contenedor: la JVM tarda
 * ~28 s en arrancar, así que si el contenedor se duerme el siguiente usuario
 * paga ese arranque completo. No toca la base de datos a propósito — solo debe
 * probar que Spring está atendiendo.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        return new ResponseEntity<>(Map.of("status", "UP"), HttpStatus.OK);
    }
}
