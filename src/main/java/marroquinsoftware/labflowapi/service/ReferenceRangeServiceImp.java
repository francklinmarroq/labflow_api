package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.AgeRange;
import marroquinsoftware.labflowapi.model.Parameter;
import marroquinsoftware.labflowapi.model.ReferenceRange;
import marroquinsoftware.labflowapi.model.Sex;
import marroquinsoftware.labflowapi.payload.ReferenceRangeDTO;
import marroquinsoftware.labflowapi.payload.ReferenceRangeResponse;
import marroquinsoftware.labflowapi.repositories.AgeRangeRepository;
import marroquinsoftware.labflowapi.repositories.ParameterRepository;
import marroquinsoftware.labflowapi.repositories.ReferenceRangeRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReferenceRangeServiceImp implements ReferenceRangeService {

    @Autowired
    private ReferenceRangeRepository referenceRangeRepository;

    @Autowired
    private ParameterRepository parameterRepository;

    @Autowired
    private AgeRangeRepository ageRangeRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public ReferenceRangeResponse getRangesByParameter(Long parameterId, Integer pageNumber, Integer pageSize, String sortBy, String sortDir) {
        parameterRepository.findById(parameterId)
                .orElseThrow(() -> new ResourceNotFoundException("Parameter", "parameterId", parameterId));

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<ReferenceRange> page = referenceRangeRepository.findByParameterId(parameterId, pageable);

        if (page.isEmpty()) {
            throw new APIException("No reference ranges found for parameter with id: " + parameterId);
        }

        List<ReferenceRangeDTO> dtos = page.getContent().stream()
                .map(this::toDTO)
                .toList();

        ReferenceRangeResponse response = new ReferenceRangeResponse();
        response.setContent(dtos);
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getNumberOfElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    @Override
    public ReferenceRangeDTO createReferenceRange(Long parameterId, ReferenceRangeDTO dto) {
        Parameter parameter = parameterRepository.findById(parameterId)
                .orElseThrow(() -> new ResourceNotFoundException("Parameter", "parameterId", parameterId));

        ReferenceRange range = modelMapper.map(dto, ReferenceRange.class);
        range.setParameter(parameter);
        range.setAgeRange(resolveAgeRange(dto.getAgeRangeId()));

        return toDTO(referenceRangeRepository.save(range));
    }

    @Override
    public ReferenceRangeDTO updateReferenceRange(Long parameterId, Long rangeId, ReferenceRangeDTO dto) {
        parameterRepository.findById(parameterId)
                .orElseThrow(() -> new ResourceNotFoundException("Parameter", "parameterId", parameterId));

        ReferenceRange range = referenceRangeRepository.findById(rangeId)
                .orElseThrow(() -> new ResourceNotFoundException("ReferenceRange", "rangeId", rangeId));

        if (!range.getParameter().getId().equals(parameterId)) {
            throw new APIException("Reference range with id: " + rangeId + " does not belong to parameter with id: " + parameterId);
        }

        Parameter parameter = range.getParameter();
        modelMapper.map(dto, range);
        range.setId(rangeId);
        range.setParameter(parameter);
        range.setAgeRange(resolveAgeRange(dto.getAgeRangeId()));

        return toDTO(referenceRangeRepository.save(range));
    }

    @Override
    public ReferenceRangeDTO deleteReferenceRange(Long parameterId, Long rangeId) {
        parameterRepository.findById(parameterId)
                .orElseThrow(() -> new ResourceNotFoundException("Parameter", "parameterId", parameterId));

        ReferenceRange range = referenceRangeRepository.findById(rangeId)
                .orElseThrow(() -> new ResourceNotFoundException("ReferenceRange", "rangeId", rangeId));

        if (!range.getParameter().getId().equals(parameterId)) {
            throw new APIException("Reference range with id: " + rangeId + " does not belong to parameter with id: " + parameterId);
        }

        referenceRangeRepository.delete(range);
        return toDTO(range);
    }

    @Override
    public List<ReferenceRangeDTO> findApplicable(Long parameterId, Sex sex, Integer ageDays) {
        parameterRepository.findById(parameterId)
                .orElseThrow(() -> new ResourceNotFoundException("Parameter", "parameterId", parameterId));

        return referenceRangeRepository.findApplicable(parameterId, sex, ageDays)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    private AgeRange resolveAgeRange(Long ageRangeId) {
        if (ageRangeId == null) return null;
        return ageRangeRepository.findById(ageRangeId)
                .orElseThrow(() -> new ResourceNotFoundException("AgeRange", "ageRangeId", ageRangeId));
    }

    private ReferenceRangeDTO toDTO(ReferenceRange range) {
        ReferenceRangeDTO dto = modelMapper.map(range, ReferenceRangeDTO.class);
        dto.setParameterId(range.getParameter().getId());
        dto.setAgeRangeId(range.getAgeRange() != null ? range.getAgeRange().getId() : null);
        return dto;
    }
}
