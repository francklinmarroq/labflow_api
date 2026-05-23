package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.AgeRangeDTO;
import marroquinsoftware.labflowapi.payload.AgeRangeResponse;

public interface AgeRangeService {

    AgeRangeResponse getAllAgeRanges(Integer pageNumber, Integer pageSize, String sortBy, String sortDir);

    AgeRangeDTO createAgeRange(AgeRangeDTO dto);

    AgeRangeDTO updateAgeRange(AgeRangeDTO dto, Long id);

    AgeRangeDTO deleteAgeRange(Long id);
}
