package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Parameter;
import marroquinsoftware.labflowapi.model.Unit;
import marroquinsoftware.labflowapi.payload.ParameterDTO;
import marroquinsoftware.labflowapi.payload.ParameterResponse;
import marroquinsoftware.labflowapi.repositories.ParameterRepository;
import marroquinsoftware.labflowapi.repositories.UnitRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ParameterServiceImp implements ParameterService {

    @Autowired
    private ParameterRepository parameterRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public ParameterResponse getAllParameters(Integer pageNumber, Integer pageSize, String sortBy, String sortDir) {
        Sort sortByAndOrder = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Parameter> parameterPage = parameterRepository.findAll(pageDetails);
        // Un catálogo vacío no es un error: se devuelve la página sin contenido.
        List<Parameter> savedParameters = parameterPage.getContent();
        List<ParameterDTO> parameterDTOs = savedParameters.stream()
                .map(parameter -> modelMapper.map(parameter, ParameterDTO.class))
                .toList();
        ParameterResponse parameterResponse = new ParameterResponse();
        parameterResponse.setPageNumber(parameterPage.getNumber());
        parameterResponse.setPageSize(parameterPage.getSize());
        parameterResponse.setTotalElements(parameterPage.getTotalElements());
        parameterResponse.setTotalPages(parameterPage.getTotalPages());
        parameterResponse.setLastPage(parameterPage.isLast());
        parameterResponse.setContent(parameterDTOs);
        return parameterResponse;
    }

    @Override
    public ParameterDTO createParameter(ParameterDTO parameterDTO) {
        Parameter existing = parameterRepository.findByName(parameterDTO.getName());
        if (existing != null) {
            throw new APIException("Ya existe un parámetro con el nombre '" + parameterDTO.getName() + "'.");
        }
        Parameter parameter = modelMapper.map(parameterDTO, Parameter.class);
        if (parameterDTO.getUnitId() != null) {
            Unit unit = unitRepository.findById(parameterDTO.getUnitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", parameterDTO.getUnitId()));
            parameter.setUnit(unit);
        }
        Parameter saved = parameterRepository.save(parameter);
        return modelMapper.map(saved, ParameterDTO.class);
    }

    @Override
    public ParameterDTO updateParameter(ParameterDTO parameterDTO, Long id) {
        Parameter parameter = parameterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parameter", "parameterId", id));
        parameter.setName(parameterDTO.getName());
        parameter.setSection(parameterDTO.getSection());
        parameter.setValueType(parameterDTO.getValueType());
        if (parameterDTO.getUnitId() != null) {
            Unit unit = unitRepository.findById(parameterDTO.getUnitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", parameterDTO.getUnitId()));
            parameter.setUnit(unit);
        } else {
            parameter.setUnit(null);
        }
        Parameter saved = parameterRepository.save(parameter);
        return modelMapper.map(saved, ParameterDTO.class);
    }

    @Override
    public ParameterDTO deleteParameter(Long id) {
        Parameter parameter = parameterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parameter", "parameterId", id));
        parameterRepository.delete(parameter);
        return modelMapper.map(parameter, ParameterDTO.class);
    }
}
