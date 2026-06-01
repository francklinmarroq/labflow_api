package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.Parameter;
import marroquinsoftware.labflowapi.model.TestResult;
import marroquinsoftware.labflowapi.model.TestRun;
import marroquinsoftware.labflowapi.payload.TestResultDTO;
import marroquinsoftware.labflowapi.payload.TestRunDTO;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.ParameterRepository;
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

        List<TestResult> results = dto.getResults().stream().map(rdto -> {
            Parameter parameter = parameterRepository.findById(rdto.getParameterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parameter", "parameterId", rdto.getParameterId()));
            TestResult result = new TestResult();
            result.setTestRun(savedRun);
            result.setParameter(parameter);
            result.setValue(rdto.getValue());
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
        return dto;
    }
}
