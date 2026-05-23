package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.ParameterDTO;
import marroquinsoftware.labflowapi.payload.ParameterResponse;

public interface ParameterService {

    ParameterResponse getAllParameters(Integer pageNumber, Integer pageSize, String sortBy, String sortDir);

    ParameterDTO createParameter(ParameterDTO parameterDTO);

    ParameterDTO updateParameter(ParameterDTO parameterDTO, Long id);

    ParameterDTO deleteParameter(Long id);

}
