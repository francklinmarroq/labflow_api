package marroquinsoftware.labflowapi.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.TestResult;
import marroquinsoftware.labflowapi.model.TestRun;
import marroquinsoftware.labflowapi.payload.ReferenceRangeDTO;
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

    @Autowired
    private ObjectMapper objectMapper;

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
            throw new APIException("El resultado no pertenece a la corrida indicada. Recargue la página e intente de nuevo.");
        }
        result.setValue(dto.getValue());
        return toDTO(testResultRepository.save(result));
    }

    @Override
    public TestResultDTO deleteResult(Long runId, Long resultId) {
        TestResult result = testResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("TestResult", "resultId", resultId));
        if (!result.getTestRun().getId().equals(runId)) {
            throw new APIException("El resultado no pertenece a la corrida indicada. Recargue la página e intente de nuevo.");
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
        dto.setReferenceRanges(parseSnapshot(result.getReferenceRangesSnapshot()));
        return dto;
    }

    private List<ReferenceRangeDTO> parseSnapshot(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<ReferenceRangeDTO>>() {});
        } catch (JacksonException e) {
            return null;
        }
    }
}
