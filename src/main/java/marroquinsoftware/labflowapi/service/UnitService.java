package marroquinsoftware.labflowapi.service;

import java.util.List;

import marroquinsoftware.labflowapi.model.Unit;
import marroquinsoftware.labflowapi.payload.UnitDTO;
import marroquinsoftware.labflowapi.payload.UnitResponse;

public interface UnitService {
    UnitResponse getAllUnits(Integer pageNumber, Integer pageSize, String sortBy, String sortDir);

    UnitDTO createUnit(UnitDTO unitDTO);

    UnitDTO deleteUnit(Long unitId);

    UnitDTO updateUnit(UnitDTO unitDTO, Long unitId);

}
