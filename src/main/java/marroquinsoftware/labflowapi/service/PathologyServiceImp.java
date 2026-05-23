package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Pathology;
import marroquinsoftware.labflowapi.payload.PathologyDTO;
import marroquinsoftware.labflowapi.payload.PathologyResponse;
import marroquinsoftware.labflowapi.repositories.PathologyRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PathologyServiceImp implements PathologyService {

    @Autowired
    private PathologyRepository pathologyRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public PathologyResponse getAllPathologies(Integer pageNumber, Integer pageSize, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<Pathology> page = pathologyRepository.findAll(pageable);
        if (page.isEmpty()) {
            throw new APIException("There are no pathologies saved.");
        }
        List<PathologyDTO> dtos = page.getContent().stream()
                .map(p -> modelMapper.map(p, PathologyDTO.class))
                .toList();
        PathologyResponse response = new PathologyResponse();
        response.setContent(dtos);
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    @Override
    public PathologyDTO createPathology(PathologyDTO dto) {
        if (pathologyRepository.findByName(dto.getName()) != null) {
            throw new APIException("Pathology with name: " + dto.getName() + " already exists.");
        }
        Pathology saved = pathologyRepository.save(modelMapper.map(dto, Pathology.class));
        return modelMapper.map(saved, PathologyDTO.class);
    }

    @Override
    public PathologyDTO updatePathology(PathologyDTO dto, Long id) {
        Pathology pathology = pathologyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pathology", "pathologyId", id));
        Pathology existing = pathologyRepository.findByName(dto.getName());
        if (existing != null && !existing.getId().equals(id)) {
            throw new APIException("Pathology with name: " + dto.getName() + " already exists.");
        }
        pathology.setName(dto.getName());
        return modelMapper.map(pathologyRepository.save(pathology), PathologyDTO.class);
    }

    @Override
    public PathologyDTO deletePathology(Long id) {
        Pathology pathology = pathologyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pathology", "pathologyId", id));
        pathologyRepository.delete(pathology);
        return modelMapper.map(pathology, PathologyDTO.class);
    }
}
