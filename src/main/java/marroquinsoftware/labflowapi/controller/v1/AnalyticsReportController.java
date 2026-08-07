package marroquinsoftware.labflowapi.controller.v1;

import marroquinsoftware.labflowapi.payload.CollectionsReportDTO;
import marroquinsoftware.labflowapi.payload.SalesReportDTO;
import marroquinsoftware.labflowapi.payload.TestsVolumeDTO;
import marroquinsoftware.labflowapi.payload.UserProductivityDTO;
import marroquinsoftware.labflowapi.service.AnalyticsReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Reportería operativa y de ventas ({@code /api/v1/reports/...}). Complementa a
 * {@link AccountingReportController} (mismo prefijo). Cada endpoint exige el
 * permiso REPORTS_VIEW; el laboratorio (tenant) lo filtra Hibernate por @TenantId.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class AnalyticsReportController {

    @Autowired
    private AnalyticsReportService analyticsReportService;

    /** Volumen de exámenes realizados por tipo en un rango; {@code area} opcional. */
    @GetMapping("/tests-volume")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    public ResponseEntity<TestsVolumeDTO> getTestsVolume(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String area) {
        return new ResponseEntity<>(analyticsReportService.getTestsVolume(from, to, area), HttpStatus.OK);
    }

    /** Ventas facturadas en un rango; {@code groupBy} = day (por defecto) o month. */
    @GetMapping("/sales")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    public ResponseEntity<SalesReportDTO> getSales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "day") String groupBy) {
        return new ResponseEntity<>(analyticsReportService.getSales(from, to, groupBy), HttpStatus.OK);
    }

    /** Cobros (pagos activos) recibidos en un rango, más el saldo por cobrar actual. */
    @GetMapping("/collections")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    public ResponseEntity<CollectionsReportDTO> getCollections(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ResponseEntity<>(analyticsReportService.getCollections(from, to), HttpStatus.OK);
    }

    /** Producción por usuario (facturación y cobros) en un rango. */
    @GetMapping("/user-productivity")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    public ResponseEntity<UserProductivityDTO> getUserProductivity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ResponseEntity<>(analyticsReportService.getUserProductivity(from, to), HttpStatus.OK);
    }
}
