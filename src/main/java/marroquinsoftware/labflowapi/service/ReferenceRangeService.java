package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.Sex;
import marroquinsoftware.labflowapi.payload.ReferenceRangeDTO;
import marroquinsoftware.labflowapi.payload.ReferenceRangeResponse;

import java.util.List;

public interface ReferenceRangeService {

    ReferenceRangeResponse getRangesByParameter(Long parameterId, Integer pageNumber, Integer pageSize, String sortBy, String sortDir);

    List<ReferenceRangeDTO> getRangesByParameterIds(List<Long> parameterIds);

    ReferenceRangeDTO createReferenceRange(Long parameterId, ReferenceRangeDTO dto);

    ReferenceRangeDTO updateReferenceRange(Long parameterId, Long rangeId, ReferenceRangeDTO dto);

    ReferenceRangeDTO deleteReferenceRange(Long parameterId, Long rangeId);

    List<ReferenceRangeDTO> findApplicable(Long parameterId, Sex sex, Integer ageDays);
}
