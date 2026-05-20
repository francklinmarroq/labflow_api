package marroquinsoftware.labflowapi.service;

import java.util.List;

import marroquinsoftware.labflowapi.model.Unit;
import marroquinsoftware.labflowapi.payload.UnitResponse;

public interface UnitService {
    UnitResponse getAllUnits();

    void createUnit(Unit unit);

    String deleteUnit(Long unitId);

    Unit updateUnit(Unit unit, Long unitId);

}
