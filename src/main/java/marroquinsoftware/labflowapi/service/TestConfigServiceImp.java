package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Parameter;
import marroquinsoftware.labflowapi.model.Test;
import marroquinsoftware.labflowapi.model.TestConfig;
import marroquinsoftware.labflowapi.model.TestConfigParameter;
import marroquinsoftware.labflowapi.payload.TestConfigDTO;
import marroquinsoftware.labflowapi.payload.TestConfigResponse;
import marroquinsoftware.labflowapi.repositories.ParameterRepository;
import marroquinsoftware.labflowapi.repositories.TestConfigRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TestConfigServiceImp implements TestConfigService {

    @Autowired
    private TestConfigRepository testConfigRepository;

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private ParameterRepository parameterRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public TestConfigResponse getAllTestConfigs(Integer pageNumber, Integer pageSize, String sortBy, String sortDir) {
        return buildPagedResponse(testConfigRepository.findAll(buildPageable(pageNumber, pageSize, sortBy, sortDir)));
    }

    @Override
    public TestConfigResponse getActiveTestConfigs(Integer pageNumber, Integer pageSize, String sortBy, String sortDir) {
        return buildPagedResponse(testConfigRepository.findByActive(true, buildPageable(pageNumber, pageSize, sortBy, sortDir)));
    }

    @Override
    public TestConfigDTO createTestConfig(TestConfigDTO dto) {
        if (testConfigRepository.findByName(dto.getName()) != null) {
            throw new APIException("Test config with name: " + dto.getName() + " already exists.");
        }
        TestConfig config = new TestConfig();
        config.setTest(resolveTest(dto.getTestId()));
        config.setName(dto.getName());
        config.setActive(dto.isActive());
        applyParameters(config, dto.getParameterIds());
        return toDTO(testConfigRepository.save(config));
    }

    @Override
    public TestConfigDTO updateTestConfig(TestConfigDTO dto, Long id) {
        TestConfig config = testConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TestConfig", "id", id));

        TestConfig existing = testConfigRepository.findByName(dto.getName());
        if (existing != null && !existing.getId().equals(id)) {
            throw new APIException("Test config with name: " + dto.getName() + " already exists.");
        }

        config.setTest(resolveTest(dto.getTestId()));
        config.setName(dto.getName());
        config.setActive(dto.isActive());
        applyParameters(config, dto.getParameterIds());
        return toDTO(testConfigRepository.save(config));
    }

    @Override
    public TestConfigDTO deleteTestConfig(Long id) {
        TestConfig config = testConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TestConfig", "id", id));
        testConfigRepository.delete(config);
        return toDTO(config);
    }

    private Test resolveTest(Long testId) {
        return testRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Test", "testId", testId));
    }

    // Reemplaza los parámetros del perfil conservando el orden recibido desde el
    // cliente: la posición de cada uno se guarda en display_order y es la que
    // respeta el reporte al imprimir. Se muta la colección existente (en vez de
    // reasignarla) para que orphanRemoval elimine las filas que ya no están.
    private void applyParameters(TestConfig config, List<Long> parameterIds) {
        config.getConfigParameters().clear();
        int order = 0;
        for (Long pid : parameterIds) {
            Parameter parameter = parameterRepository.findById(pid)
                    .orElseThrow(() -> new ResourceNotFoundException("Parameter", "parameterId", pid));
            config.getConfigParameters().add(new TestConfigParameter(config, parameter, order++));
        }
    }

    private Pageable buildPageable(Integer pageNumber, Integer pageSize, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        return PageRequest.of(pageNumber, pageSize, sort);
    }

    private TestConfigResponse buildPagedResponse(Page<TestConfig> page) {
        List<TestConfigDTO> dtos = page.getContent().stream().map(this::toDTO).toList();
        TestConfigResponse response = new TestConfigResponse();
        response.setContent(dtos);
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    private TestConfigDTO toDTO(TestConfig config) {
        TestConfigDTO dto = modelMapper.map(config, TestConfigDTO.class);
        dto.setTestId(config.getTest().getId());
        dto.setParameterIds(config.getConfigParameters().stream()
                .map(cp -> cp.getParameter().getId())
                .collect(Collectors.toList()));
        return dto;
    }
}
