package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Test;
import marroquinsoftware.labflowapi.payload.TestDTO;
import marroquinsoftware.labflowapi.payload.TestResponse;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TestServiceImp implements TestService {

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public TestResponse getAllTests(Integer pageNumber, Integer pageSize, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<Test> page = testRepository.findAll(pageable);

        List<TestDTO> dtos = page.getContent().stream()
                .map(t -> modelMapper.map(t, TestDTO.class))
                .toList();
        TestResponse response = new TestResponse();
        response.setContent(dtos);
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    @Override
    public TestDTO createTest(TestDTO dto) {
        if (testRepository.findByName(dto.getName()) != null) {
            throw new APIException("Ya existe un examen con el nombre '" + dto.getName() + "'.");
        }
        Test test = modelMapper.map(dto, Test.class);
        return modelMapper.map(testRepository.save(test), TestDTO.class);
    }

    @Override
    public TestDTO updateTest(TestDTO dto, Long id) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Test", "id", id));

        Test existing = testRepository.findByName(dto.getName());
        if (existing != null && !existing.getId().equals(id)) {
            throw new APIException("Ya existe un examen con el nombre '" + dto.getName() + "'.");
        }

        modelMapper.map(dto, test);
        test.setId(id);
        return modelMapper.map(testRepository.save(test), TestDTO.class);
    }

    @Override
    public TestDTO deleteTest(Long id) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Test", "id", id));
        testRepository.delete(test);
        return modelMapper.map(test, TestDTO.class);
    }
}
