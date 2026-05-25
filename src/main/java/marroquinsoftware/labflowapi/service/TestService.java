package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.TestDTO;
import marroquinsoftware.labflowapi.payload.TestResponse;

public interface TestService {
    TestResponse getAllTests(Integer pageNumber, Integer pageSize, String sortBy, String sortDir);
    TestDTO createTest(TestDTO dto);
    TestDTO updateTest(TestDTO dto, Long id);
    TestDTO deleteTest(Long id);
}
