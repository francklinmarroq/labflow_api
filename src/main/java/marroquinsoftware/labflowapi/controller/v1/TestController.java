package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.TestDTO;
import marroquinsoftware.labflowapi.payload.TestResponse;
import marroquinsoftware.labflowapi.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tests")
public class TestController {

    @Autowired
    private TestService testService;

    @GetMapping
    public ResponseEntity<TestResponse> getAllTests(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_TESTS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        return new ResponseEntity<>(testService.getAllTests(pageNumber, pageSize, sortBy, sortOrder), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<TestDTO> createTest(@Valid @RequestBody TestDTO dto) {
        return new ResponseEntity<>(testService.createTest(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{testId}")
    public ResponseEntity<TestDTO> updateTest(@Valid @RequestBody TestDTO dto, @PathVariable Long testId) {
        return new ResponseEntity<>(testService.updateTest(dto, testId), HttpStatus.OK);
    }

    @DeleteMapping("/{testId}")
    public ResponseEntity<TestDTO> deleteTest(@PathVariable Long testId) {
        return new ResponseEntity<>(testService.deleteTest(testId), HttpStatus.OK);
    }
}
