package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.LabTestDTO;

import java.util.List;

public interface LabTestService {
    List<LabTestDTO> getTestsByOrder(Long orderId);
    LabTestDTO addTestToOrder(Long orderId, LabTestDTO dto);
    LabTestDTO removeTestFromOrder(Long orderId, Long testId);
}
