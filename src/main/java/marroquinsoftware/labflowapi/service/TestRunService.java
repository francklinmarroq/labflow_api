package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.TestRunDTO;

import java.util.List;

public interface TestRunService {
    List<TestRunDTO> getRunsByTest(Long testId);
    TestRunDTO addRunToTest(Long testId, TestRunDTO dto);
    TestRunDTO verifyRun(Long testId, Long runId);
    TestRunDTO deleteRun(Long testId, Long runId);
}
