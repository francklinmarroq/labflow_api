package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.TestResultDTO;

import java.util.List;

public interface TestResultService {
    List<TestResultDTO> getResultsByRun(Long runId);
    TestResultDTO updateResult(Long runId, Long resultId, TestResultDTO dto);
    TestResultDTO deleteResult(Long runId, Long resultId);
}
