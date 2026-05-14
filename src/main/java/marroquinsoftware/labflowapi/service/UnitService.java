package marroquinsoftware.labflowapi.service;

import java.util.List;

import marroquinsoftware.labflowapi.model.Unit;

public interface UnitService {
    List<Unit> getAllUnits();

    void createUnit(Unit unit);

    String deleteUnit(Long unitId);

    Unit updateUnit(Unit unit, Long unitId);

}
