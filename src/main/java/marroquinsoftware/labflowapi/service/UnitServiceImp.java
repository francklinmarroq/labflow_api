package marroquinsoftware.labflowapi.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import marroquinsoftware.labflowapi.model.Unit;

@Service
public class UnitServiceImp implements UnitService {
    private List<Unit> units = new ArrayList<>();

    @Override
    public List<Unit> getAllUnits() {
        return units;
    }

    @Override
    public void createUnit(Unit unit) {
        unit.setUnitId((long) (units.size() + 1));
        units.add(unit);
    }

    @Override
    public String deleteUnit(Long unitId) {
        Unit unit = units.stream()
                .filter(c -> c.getUnitId().equals(unitId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found."));
        units.remove(unit);

        return "Unit with id: " + unitId + " deleted successfully.";
    }

    @Override
    public Unit updateUnit(Unit unit, Long unitId) {
        Optional<Unit> optionalUnit = units.stream().filter(c -> c.getUnitId().equals(unitId)).findFirst();

        if (optionalUnit.isPresent()) {
            Unit existingUnit = optionalUnit.get();
            existingUnit.setUnitSymbol(unit.getUnitSymbol());
            return existingUnit;
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resourse not found.");
        }

    }

}
