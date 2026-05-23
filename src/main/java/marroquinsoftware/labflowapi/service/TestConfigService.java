package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.TestConfigDTO;
import marroquinsoftware.labflowapi.payload.TestConfigResponse;

public interface TestConfigService {
    TestConfigResponse getAllTestConfigs(Integer pageNumber, Integer pageSize, String sortBy, String sortDir);
    TestConfigResponse getActiveTestConfigs(Integer pageNumber, Integer pageSize, String sortBy, String sortDir);
    TestConfigDTO createTestConfig(TestConfigDTO dto);
    TestConfigDTO updateTestConfig(TestConfigDTO dto, Long id);
    TestConfigDTO deleteTestConfig(Long id);
}
