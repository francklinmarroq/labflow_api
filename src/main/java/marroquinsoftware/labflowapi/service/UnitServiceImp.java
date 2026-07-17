package marroquinsoftware.labflowapi.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.payload.UnitDTO;
import marroquinsoftware.labflowapi.payload.UnitResponse;
import marroquinsoftware.labflowapi.repositories.UnitRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import marroquinsoftware.labflowapi.model.Unit;

@Service
public class UnitServiceImp implements UnitService {

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public UnitResponse getAllUnits(Integer pageNumer, Integer pageSize, String sortBy, String sortDir) {
        Sort sortByAndOrder = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortDir).descending();
        Pageable pageDetails = PageRequest.of(pageNumer, pageSize, sortByAndOrder);
        Page<Unit> unitPage = unitRepository.findAll(pageDetails);
        // Un catálogo vacío no es un error: se devuelve la página sin contenido.
        List<Unit> savedUnits = unitPage.getContent();
        List<UnitDTO> unitDTOS = savedUnits.stream().map(unit -> modelMapper.map(unit, UnitDTO.class)).toList();
        UnitResponse unitResponse = new UnitResponse();
        unitResponse.setPageNumber(unitPage.getNumber());
        unitResponse.setPageSize(unitPage.getSize());
        unitResponse.setTotalElements(unitPage.getTotalElements());
        unitResponse.setTotalPages(unitPage.getTotalPages());
        unitResponse.setLastPage(unitPage.isLast());
        unitResponse.setContent(unitDTOS);

        return unitResponse;
    }

    @Override
    public UnitDTO createUnit(UnitDTO unitDTO) {
        Unit unit = modelMapper.map(unitDTO, Unit.class);
        Unit existingUnit = unitRepository.findByUnitSymbol(unit.getUnitSymbol());
        if (existingUnit != null){
            throw new APIException("Ya existe una unidad con el símbolo '" + unit.getUnitSymbol() + "'.");
        }
        Unit savedUnit = unitRepository.save(unit);
        return modelMapper.map(savedUnit, UnitDTO.class);
    }

    @Override
    public UnitDTO updateUnit(UnitDTO unitDTO, Long id) {
        Unit unit = modelMapper.map(unitDTO, Unit.class);
        Optional<Unit> optionalUnit = unitRepository.findById(id);

        if (optionalUnit.isPresent()) {
            Unit existingUnit = optionalUnit.get();
            existingUnit.setUnitSymbol(unit.getUnitSymbol());
            Unit savedUnit = unitRepository.save(existingUnit);
            return modelMapper.map(savedUnit, UnitDTO.class);
        } else {
            throw  new ResourceNotFoundException("Unit","unitId", id);
        }

    }

    @Override
    public UnitDTO deleteUnit(Long id) {
        Optional<Unit> unitOptional = unitRepository.findById(id);
        Unit unit = unitOptional.orElseThrow(() -> new ResourceNotFoundException("Unit","unitId", id));
        unitRepository.delete(unit);

        return modelMapper.map(unit, UnitDTO.class);
    }


}
