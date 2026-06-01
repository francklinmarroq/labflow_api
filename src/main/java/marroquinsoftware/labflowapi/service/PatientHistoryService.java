package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.PatientTestHistoryDTO;

import java.time.Instant;
import java.util.List;

public interface PatientHistoryService {
    List<PatientTestHistoryDTO> getPatientHistory(Long customerId, String testName, Instant dateFrom, Instant dateTo);
}
