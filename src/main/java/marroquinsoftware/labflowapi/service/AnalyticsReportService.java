package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.CollectionsReportDTO;
import marroquinsoftware.labflowapi.payload.SalesReportDTO;
import marroquinsoftware.labflowapi.payload.TestsVolumeDTO;
import marroquinsoftware.labflowapi.payload.UserProductivityDTO;

import java.time.LocalDate;

/**
 * Reportería operativa y de ventas: volumen de exámenes realizados, ventas,
 * cobros y producción por usuario, todo por rango de fechas. El laboratorio
 * (tenant) lo filtra Hibernate por @TenantId en cada consulta.
 */
public interface AnalyticsReportService {

    TestsVolumeDTO getTestsVolume(LocalDate from, LocalDate to, String area);

    SalesReportDTO getSales(LocalDate from, LocalDate to, String groupBy);

    CollectionsReportDTO getCollections(LocalDate from, LocalDate to);

    UserProductivityDTO getUserProductivity(LocalDate from, LocalDate to);
}
