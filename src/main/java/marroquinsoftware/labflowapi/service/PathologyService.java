package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.PathologyDTO;
import marroquinsoftware.labflowapi.payload.PathologyResponse;

public interface PathologyService {
    PathologyResponse getAllPathologies(Integer pageNumber, Integer pageSize, String sortBy, String sortDir);
    PathologyDTO createPathology(PathologyDTO dto);
    PathologyDTO updatePathology(PathologyDTO dto, Long id);
    PathologyDTO deletePathology(Long id);
}
