package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.TestResult;
import marroquinsoftware.labflowapi.model.TestRun;
import marroquinsoftware.labflowapi.payload.TestResultDTO;
import marroquinsoftware.labflowapi.repositories.TestResultRepository;
import marroquinsoftware.labflowapi.repositories.TestRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TestResultServiceImp implements TestResultService {

    @Autowired
    private TestRunRepository testRunRepository;

    @Autowired
    private TestResultRepository testResultRepository;

    @Override
    public List<TestResultDTO> getResultsByRun(Long runId) {
        if (!testRunRepository.existsById(runId)) {
            throw new ResourceNotFoundException("TestRun", "runId", runId);
        }
        return testResultRepository.findByTestRun_Id(runId).stream().map(this::toDTO).toList();
    }

    @Override
    public TestResultDTO updateResult(Long runId, Long resultId, TestResultDTO dto) {
        TestResult result = testResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("TestResult", "resultId", resultId));
        if (!result.getTestRun().getId().equals(runId)) {
            throw new APIException("Result with id: " + resultId + " does not belong to run with id: " + runId);
        }
        result.setValue(dto.getValue());
        return toDTO(testResultRepository.save(result));
    }

    @Override
    public TestResultDTO deleteResult(Long runId, Long resultId) {
        TestResult result = testResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("TestResult", "resultId", resultId));
        if (!result.getTestRun().getId().equals(runId)) {
            throw new APIException("Result with id: " + resultId + " does not belong to run with id: " + runId);
        }
        testResultRepository.delete(result);
        return toDTO(result);
    }

    private TestResultDTO toDTO(TestResult result) {
        TestResultDTO dto = new TestResultDTO();
        dto.setId(result.getId());
        dto.setTestRunId(result.getTestRun().getId());
        dto.setParameterId(result.getParameter().getId());
        dto.setValue(result.getValue());
        return dto;
    }
}
