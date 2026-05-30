package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.LaboratoryDTO;

public interface LaboratoryService {
    LaboratoryDTO getLaboratory();
    LaboratoryDTO createLaboratory(LaboratoryDTO dto);
    LaboratoryDTO updateLaboratory(LaboratoryDTO dto, Long id);
}
