package marroquinsoftware.labflowapi.controller.v1;

import marroquinsoftware.labflowapi.payload.PublicReportDTO;
import marroquinsoftware.labflowapi.service.PublicReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos (sin autenticación) para que el paciente vea sus resultados
 * con el token del enlace/QR impreso en el reporte. El laboratorio (tenant) lo
 * resuelve el {@code AuthTokenFilter} a partir del token; ver SecurityConfig, que
 * abre /api/v1/public/**.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicReportController {

    private final PublicReportService publicReportService;

    public PublicReportController(PublicReportService publicReportService) {
        this.publicReportService = publicReportService;
    }

    @GetMapping("/orders/{token}")
    public ResponseEntity<PublicReportDTO> getPublicReport(@PathVariable String token) {
        return new ResponseEntity<>(publicReportService.getPublicReport(token), HttpStatus.OK);
    }
}
