package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.PublicReportDTO;

public interface PublicReportService {

    /** Arma el reporte público de la orden identificada por su token opaco. */
    PublicReportDTO getPublicReport(String token);
}
