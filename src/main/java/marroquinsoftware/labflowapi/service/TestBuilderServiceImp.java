package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Parameter;
import marroquinsoftware.labflowapi.model.ReferenceRange;
import marroquinsoftware.labflowapi.model.Test;
import marroquinsoftware.labflowapi.model.TestConfig;
import marroquinsoftware.labflowapi.model.TestConfigParameter;
import marroquinsoftware.labflowapi.payload.ParameterDTO;
import marroquinsoftware.labflowapi.payload.ReferenceRangeDTO;
import marroquinsoftware.labflowapi.payload.TestConfigDTO;
import marroquinsoftware.labflowapi.payload.TestDTO;
import marroquinsoftware.labflowapi.payload.TestFullDTO;
import marroquinsoftware.labflowapi.payload.TestFullParameterDTO;
import marroquinsoftware.labflowapi.repositories.ReferenceRangeRepository;
import marroquinsoftware.labflowapi.repositories.TestConfigRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reúne el examen, su perfil y sus parámetros con rangos en una operación atómica.
 * No accede directo a las entidades para escribir: delega en los servicios de cada
 * pieza (que ya validan nombres, resuelven FKs y mapean DTOs) y los envuelve en una
 * sola transacción, de modo que si algo falla no queda un examen a medias.
 */
@Service
public class TestBuilderServiceImp implements TestBuilderService {

    @Autowired
    private TestService testService;

    @Autowired
    private ParameterService parameterService;

    @Autowired
    private ReferenceRangeService referenceRangeService;

    @Autowired
    private TestConfigService testConfigService;

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private TestConfigRepository testConfigRepository;

    @Autowired
    private ReferenceRangeRepository referenceRangeRepository;

    @Override
    @Transactional(readOnly = true)
    public TestFullDTO getFull(Long testId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Test", "id", testId));

        TestFullDTO dto = new TestFullDTO();
        dto.setId(test.getId());
        dto.setName(test.getName());
        dto.setPrice(test.getPrice());
        dto.setCost(test.getCost());
        dto.setArea(test.getArea());

        // La UI asume 1 perfil por examen: se toma el primero si existe.
        TestConfig config = testConfigRepository.findByTestId(testId, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);

        List<TestFullParameterDTO> paramDtos = new ArrayList<>();
        if (config != null) {
            dto.setTestConfigId(config.getId());
            dto.setProfileName(config.getName());
            dto.setActive(config.isActive());
            dto.setChartType(config.getChartType());
            dto.setResultLayout(config.getResultLayout());
            dto.setChartXAxisLabel(config.getChartXAxisLabel());

            // configParameters ya viene ordenado por displayOrder (@OrderBy).
            for (TestConfigParameter cp : config.getConfigParameters()) {
                paramDtos.add(toFullParameterDTO(cp.getParameter(), cp.getChartXValue()));
            }
        } else {
            // Examen sin perfil todavía: se ofrece el nombre del examen por defecto.
            dto.setProfileName(test.getName());
            dto.setActive(true);
        }
        dto.setParameters(paramDtos);
        return dto;
    }

    @Override
    @Transactional
    public TestFullDTO createFull(TestFullDTO dto) {
        TestDTO test = testService.createTest(toTestDTO(dto));
        return persistProfile(dto, test.getId(), null);
    }

    @Override
    @Transactional
    public TestFullDTO updateFull(TestFullDTO dto, Long testId) {
        testService.updateTest(toTestDTO(dto), testId);

        // El perfil a actualizar es el que ya cuelga del examen (1:1); si por algún
        // motivo no existe, se crea.
        Long existingConfigId = testConfigRepository.findByTestId(testId, PageRequest.of(0, 1))
                .stream().findFirst().map(TestConfig::getId).orElse(null);

        return persistProfile(dto, testId, existingConfigId);
    }

    // Crea/actualiza los parámetros (con sus rangos) y luego el perfil que los une,
    // conservando el orden recibido. Devuelve el agregado ya guardado.
    private TestFullDTO persistProfile(TestFullDTO dto, Long testId, Long existingConfigId) {
        List<Long> parameterIds = new ArrayList<>();
        Map<Long, BigDecimal> chartXValues = new LinkedHashMap<>();

        for (TestFullParameterDTO row : dto.getParameters()) {
            Long parameterId = upsertParameter(row);
            syncRanges(parameterId, row.getReferenceRanges());
            parameterIds.add(parameterId);
            if (row.getChartXValue() != null) {
                chartXValues.put(parameterId, row.getChartXValue());
            }
        }

        TestConfigDTO configDTO = new TestConfigDTO();
        configDTO.setTestId(testId);
        String profileName = (dto.getProfileName() != null && !dto.getProfileName().isBlank())
                ? dto.getProfileName().trim() : dto.getName();
        configDTO.setName(profileName);
        configDTO.setParameterIds(parameterIds);
        configDTO.setActive(dto.isActive());
        configDTO.setChartType(dto.getChartType());
        configDTO.setResultLayout(dto.getResultLayout());
        configDTO.setChartXAxisLabel(dto.getChartXAxisLabel());
        configDTO.setChartXValues(chartXValues);

        if (existingConfigId != null) {
            testConfigService.updateTestConfig(configDTO, existingConfigId);
        } else {
            testConfigService.createTestConfig(configDTO);
        }

        return getFull(testId);
    }

    // id == null → crea el parámetro; id != null → reutiliza y refresca sus campos.
    // Devuelve el id del parámetro ya persistido.
    private Long upsertParameter(TestFullParameterDTO row) {
        ParameterDTO paramDTO = new ParameterDTO();
        paramDTO.setName(row.getName());
        paramDTO.setUnitId(row.getUnitId());
        paramDTO.setSection(row.getSection());
        paramDTO.setValueType(row.getValueType());

        if (row.getId() == null) {
            return parameterService.createParameter(paramDTO).getId();
        }
        return parameterService.updateParameter(paramDTO, row.getId()).getId();
    }

    // Deja los rangos del parámetro exactamente como los envía el cliente: borra los
    // que ya no vengan, actualiza los que traen id y crea los que no. Como el
    // parámetro es compartido, esto afecta a cualquier perfil que lo reutilice.
    private void syncRanges(Long parameterId, List<ReferenceRangeDTO> incoming) {
        List<ReferenceRangeDTO> rows = incoming != null ? incoming : List.of();

        Set<Long> keepIds = new HashSet<>();
        for (ReferenceRangeDTO r : rows) {
            if (r.getId() != null) keepIds.add(r.getId());
        }

        // Borra los rangos existentes que ya no estén en la lista entrante.
        List<ReferenceRange> existing = referenceRangeRepository.findByParameterId(
                parameterId, PageRequest.of(0, 1000)).getContent();
        for (ReferenceRange r : existing) {
            if (!keepIds.contains(r.getId())) {
                referenceRangeService.deleteReferenceRange(parameterId, r.getId());
            }
        }

        // Crea o actualiza los entrantes.
        for (ReferenceRangeDTO r : rows) {
            r.setParameterId(parameterId);
            if (r.getId() == null) {
                referenceRangeService.createReferenceRange(parameterId, r);
            } else {
                referenceRangeService.updateReferenceRange(parameterId, r.getId(), r);
            }
        }
    }

    private TestDTO toTestDTO(TestFullDTO dto) {
        TestDTO test = new TestDTO();
        test.setName(dto.getName());
        test.setPrice(dto.getPrice());
        test.setCost(dto.getCost());
        test.setArea(dto.getArea());
        return test;
    }

    private TestFullParameterDTO toFullParameterDTO(Parameter parameter, BigDecimal chartXValue) {
        TestFullParameterDTO row = new TestFullParameterDTO();
        row.setId(parameter.getId());
        row.setName(parameter.getName());
        row.setUnitId(parameter.getUnit() != null ? parameter.getUnit().getId() : null);
        row.setSection(parameter.getSection());
        row.setValueType(parameter.getValueType());
        row.setChartXValue(chartXValue);
        row.setReferenceRanges(loadRanges(parameter.getId()));
        return row;
    }

    private List<ReferenceRangeDTO> loadRanges(Long parameterId) {
        return referenceRangeService.getRangesByParameter(parameterId, 0, 1000, "id", "asc")
                .getContent();
    }
}
