package marroquinsoftware.labflowapi.service;

import java.util.List;
import java.util.Optional;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.repositories.UnitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import marroquinsoftware.labflowapi.model.Unit;

@Service
public class UnitServiceImp implements UnitService {

    @Autowired
    private UnitRepository unitRepository;

    @Override
    public List<Unit> getAllUnits() {
        List<Unit> savedUnits = unitRepository.findAll();
        if (savedUnits.isEmpty()){
            throw new APIException("There are no units saved.");
        }
        return savedUnits;
    }

    @Override
    public void createUnit(Unit unit) {
        Unit savedUnit = unitRepository.findByUnitSymbol(unit.getUnitSymbol());
        if (savedUnit != null){
            throw new APIException("Unit with symbol: " + unit.getUnitSymbol() + " already exists.");
        }
        unitRepository.save(unit);
    }

    @Override
    public String deleteUnit(Long unitId) {
        Optional<Unit> unitOptional = unitRepository.findById(unitId);
        Unit unit = unitOptional.orElseThrow(() -> new ResourceNotFoundException("Unit","unitId", unitId));
        unitRepository.delete(unit);

        return "Unit with id: " + unitId + " deleted successfully.";
    }

    @Override
    public Unit updateUnit(Unit unit, Long unitId) {
        List<Unit> units = unitRepository.findAll();
        Optional<Unit> optionalUnit = units.stream().filter(c -> c.getUnitId().equals(unitId)).findFirst();

        if (optionalUnit.isPresent()) {
            Unit existingUnit = optionalUnit.get();
            existingUnit.setUnitSymbol(unit.getUnitSymbol());
            return unitRepository.save(existingUnit);
        } else {
            throw  new ResourceNotFoundException("Unit","unitId", unitId);
        }

    }

}
