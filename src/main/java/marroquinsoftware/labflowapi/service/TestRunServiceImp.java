package marroquinsoftware.labflowapi.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.Parameter;
import marroquinsoftware.labflowapi.model.ReferenceRange;
import marroquinsoftware.labflowapi.model.Sex;
import marroquinsoftware.labflowapi.model.TestResult;
import marroquinsoftware.labflowapi.model.TestRun;
import marroquinsoftware.labflowapi.payload.ReferenceRangeDTO;
import marroquinsoftware.labflowapi.payload.TestResultDTO;
import marroquinsoftware.labflowapi.payload.TestRunDTO;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.ParameterRepository;
import marroquinsoftware.labflowapi.repositories.ReferenceRangeRepository;
import marroquinsoftware.labflowapi.repositories.TestResultRepository;
import marroquinsoftware.labflowapi.repositories.TestRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
public class TestRunServiceImp implements TestRunService {

    @Autowired
    private LabTestRepository labTestRepository;

    @Autowired
    private TestRunRepository testRunRepository;

    @Autowired
    private TestResultRepository testResultRepository;

    @Autowired
    private ParameterRepository parameterRepository;

    @Autowired
    private ReferenceRangeRepository referenceRangeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<TestRunDTO> getRunsByTest(Long testId) {
        if (!labTestRepository.existsById(testId)) {
            throw new ResourceNotFoundException("LabTest", "testId", testId);
        }
        return testRunRepository.findByTest_IdOrderByRunNumberAsc(testId).stream().map(this::toDTO).toList();
    }

    @Override
    public TestRunDTO addRunToTest(Long testId, TestRunDTO dto) {
        LabTest test = labTestRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "testId", testId));

        int nextRunNumber = testRunRepository.findTopByTest_IdOrderByRunNumberDesc(testId)
                .map(r -> r.getRunNumber() + 1)
                .orElse(1);

        TestRun run = new TestRun();
        run.setTest(test);
        run.setRunNumber(nextRunNumber);
        run.setPerformedAt(dto.getPerformedAt() != null ? dto.getPerformedAt() : Instant.now());
        run.setIsVerified(false);
        TestRun savedRun = testRunRepository.save(run);

        // Datos del paciente para resolver qué rangos de referencia aplican y
        // congelarlos en cada resultado. La cadena LabTest -> LabOrder -> Customer
        // es @ManyToOne (EAGER), así que ya viene cargada.
        Customer customer = test.getOrder() != null ? test.getOrder().getCustomer() : null;
        Sex sex = customer != null ? customer.getSex() : null;
        Integer ageDays = customer != null ? customer.getAgeInDays() : null;

        List<TestResult> results = dto.getResults().stream().map(rdto -> {
            Parameter parameter = parameterRepository.findById(rdto.getParameterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parameter", "parameterId", rdto.getParameterId()));
            TestResult result = new TestResult();
            result.setTestRun(savedRun);
            result.setParameter(parameter);
            result.setValue(rdto.getValue());
            result.setReferenceRangesSnapshot(buildSnapshot(rdto.getParameterId(), sex, ageDays));
            return result;
        }).toList();
        testResultRepository.saveAll(results);
        savedRun.setResults(results);
        return toDTO(savedRun);
    }

    @Override
    public TestRunDTO verifyRun(Long testId, Long runId) {
        if (!labTestRepository.existsById(testId)) {
            throw new ResourceNotFoundException("LabTest", "testId", testId);
        }
        List<TestRun> allRuns = testRunRepository.findByTest_IdOrderByRunNumberAsc(testId);
        TestRun target = allRuns.stream()
                .filter(r -> r.getId().equals(runId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", "runId", runId));
        allRuns.forEach(r -> r.setIsVerified(false));
        target.setIsVerified(true);
        testRunRepository.saveAll(allRuns);
        return toDTO(target);
    }

    @Override
    public TestRunDTO deleteRun(Long testId, Long runId) {
        TestRun run = testRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", "runId", runId));
        if (!run.getTest().getId().equals(testId)) {
            throw new APIException("Run with id: " + runId + " does not belong to test with id: " + testId);
        }
        testRunRepository.delete(run);
        return toDTO(run);
    }

    private TestRunDTO toDTO(TestRun run) {
        TestRunDTO dto = new TestRunDTO();
        dto.setId(run.getId());
        dto.setTestId(run.getTest().getId());
        dto.setRunNumber(run.getRunNumber());
        dto.setPerformedAt(run.getPerformedAt());
        dto.setIsVerified(run.getIsVerified());
        List<TestResultDTO> results = run.getResults() != null
                ? run.getResults().stream().map(this::toResultDTO).toList()
                : Collections.emptyList();
        dto.setResults(results);
        return dto;
    }

    private TestResultDTO toResultDTO(TestResult result) {
        TestResultDTO dto = new TestResultDTO();
        dto.setId(result.getId());
        dto.setTestRunId(result.getTestRun().getId());
        dto.setParameterId(result.getParameter().getId());
        dto.setValue(result.getValue());
        dto.setReferenceRanges(parseSnapshot(result.getReferenceRangesSnapshot()));
        return dto;
    }

    // Calcula los rangos aplicables al paciente y los serializa a JSON para
    // congelarlos en el resultado. Devuelve null si no hay rangos aplicables.
    private String buildSnapshot(Long parameterId, Sex sex, Integer ageDays) {
        List<ReferenceRange> applicable = referenceRangeRepository.findApplicable(parameterId, sex, ageDays);
        if (applicable.isEmpty()) return null;
        List<ReferenceRangeDTO> snapshot = applicable.stream().map(this::toRangeDTO).toList();
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException e) {
            throw new APIException("No se pudo serializar el snapshot de rangos de referencia: " + e.getMessage());
        }
    }

    private List<ReferenceRangeDTO> parseSnapshot(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<ReferenceRangeDTO>>() {});
        } catch (JacksonException e) {
            // Snapshot corrupto: se omite en lugar de romper la lectura del resultado.
            return null;
        }
    }

    private ReferenceRangeDTO toRangeDTO(ReferenceRange r) {
        ReferenceRangeDTO d = new ReferenceRangeDTO();
        d.setId(r.getId());
        d.setParameterId(r.getParameter().getId());
        d.setSex(r.getSex());
        d.setAgeRangeId(r.getAgeRange() != null ? r.getAgeRange().getId() : null);
        d.setLowerLimit(r.getLowerLimit());
        d.setLowerExclusive(r.isLowerExclusive());
        d.setUpperLimit(r.getUpperLimit());
        d.setUpperExclusive(r.isUpperExclusive());
        d.setCriticalLow(r.getCriticalLow());
        d.setCriticalHigh(r.getCriticalHigh());
        d.setInterpretationText(r.getInterpretationText());
        d.setContextKind(r.getContextKind());
        d.setContextLabel(r.getContextLabel());
        d.setContextMin(r.getContextMin());
        d.setContextMax(r.getContextMax());
        return d;
    }
}
