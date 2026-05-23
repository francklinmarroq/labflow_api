package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.AgeRange;
import marroquinsoftware.labflowapi.payload.AgeRangeDTO;
import marroquinsoftware.labflowapi.payload.AgeRangeResponse;
import marroquinsoftware.labflowapi.repositories.AgeRangeRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgeRangeServiceImp implements AgeRangeService {

    @Autowired
    private AgeRangeRepository ageRangeRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public AgeRangeResponse getAllAgeRanges(Integer pageNumber, Integer pageSize, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<AgeRange> page = ageRangeRepository.findAll(pageable);

        if (page.isEmpty()) {
            throw new APIException("There are no age ranges saved.");
        }

        List<AgeRangeDTO> dtos = page.getContent().stream()
                .map(ar -> modelMapper.map(ar, AgeRangeDTO.class))
                .toList();

        AgeRangeResponse response = new AgeRangeResponse();
        response.setContent(dtos);
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getNumberOfElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    @Override
    public AgeRangeDTO createAgeRange(AgeRangeDTO dto) {
        if (ageRangeRepository.findByName(dto.getName()) != null) {
            throw new APIException("Age range with name: " + dto.getName() + " already exists.");
        }
        AgeRange saved = ageRangeRepository.save(modelMapper.map(dto, AgeRange.class));
        return modelMapper.map(saved, AgeRangeDTO.class);
    }

    @Override
    public AgeRangeDTO updateAgeRange(AgeRangeDTO dto, Long id) {
        AgeRange ageRange = ageRangeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AgeRange", "ageRangeId", id));
        modelMapper.map(dto, ageRange);
        ageRange.setId(id);
        return modelMapper.map(ageRangeRepository.save(ageRange), AgeRangeDTO.class);
    }

    @Override
    public AgeRangeDTO deleteAgeRange(Long id) {
        AgeRange ageRange = ageRangeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AgeRange", "ageRangeId", id));
        ageRangeRepository.delete(ageRange);
        return modelMapper.map(ageRange, AgeRangeDTO.class);
    }
}
