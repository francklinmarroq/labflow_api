package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.TestConfigDTO;
import marroquinsoftware.labflowapi.payload.TestConfigResponse;
import marroquinsoftware.labflowapi.service.TestConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1/test-configs")
public class TestConfigController {

    @Autowired
    private TestConfigService testConfigService;

    @GetMapping
    public ResponseEntity<TestConfigResponse> getAllTestConfigs(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_TEST_CONFIGS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        return new ResponseEntity<>(
                testConfigService.getAllTestConfigs(pageNumber, pageSize, sortBy, sortOrder),
                HttpStatus.OK);
    }

    @GetMapping("/active")
    public ResponseEntity<TestConfigResponse> getActiveTestConfigs(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_TEST_CONFIGS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        return new ResponseEntity<>(
                testConfigService.getActiveTestConfigs(pageNumber, pageSize, sortBy, sortOrder),
                HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<TestConfigDTO> createTestConfig(@Valid @RequestBody TestConfigDTO dto) {
        return new ResponseEntity<>(testConfigService.createTestConfig(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{testConfigId}")
    public ResponseEntity<TestConfigDTO> updateTestConfig(
            @Valid @RequestBody TestConfigDTO dto,
            @PathVariable Long testConfigId) {
        return new ResponseEntity<>(testConfigService.updateTestConfig(dto, testConfigId), HttpStatus.OK);
    }

    @DeleteMapping("/{testConfigId}")
    public ResponseEntity<TestConfigDTO> deleteTestConfig(@PathVariable Long testConfigId) {
        return new ResponseEntity<>(testConfigService.deleteTestConfig(testConfigId), HttpStatus.OK);
    }
}
