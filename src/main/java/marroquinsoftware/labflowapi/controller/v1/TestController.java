package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.TestDTO;
import marroquinsoftware.labflowapi.payload.TestFullDTO;
import marroquinsoftware.labflowapi.payload.TestResponse;
import marroquinsoftware.labflowapi.service.TestBuilderService;
import marroquinsoftware.labflowapi.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tests")
public class TestController {

    @Autowired
    private TestService testService;

    @Autowired
    private TestBuilderService testBuilderService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CATALOG_VIEW','ORDERS_VIEW','ORDERS_CREATE','ORDERS_ENTER_RESULTS','ORDERS_PRINT','QUOTES_CREATE')")
    public ResponseEntity<TestResponse> getAllTests(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_TESTS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        return new ResponseEntity<>(testService.getAllTests(pageNumber, pageSize, sortBy, sortOrder), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_CREATE')")
    public ResponseEntity<TestDTO> createTest(@Valid @RequestBody TestDTO dto) {
        return new ResponseEntity<>(testService.createTest(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{testId}")
    @PreAuthorize("hasAuthority('CATALOG_EDIT')")
    public ResponseEntity<TestDTO> updateTest(@Valid @RequestBody TestDTO dto, @PathVariable Long testId) {
        return new ResponseEntity<>(testService.updateTest(dto, testId), HttpStatus.OK);
    }

    @DeleteMapping("/{testId}")
    @PreAuthorize("hasAuthority('CATALOG_DELETE')")
    public ResponseEntity<TestDTO> deleteTest(@PathVariable Long testId) {
        return new ResponseEntity<>(testService.deleteTest(testId), HttpStatus.OK);
    }

    // --- Editor unificado: examen + perfil + parámetros con rangos en un solo paso ---

    @GetMapping("/{testId}/full")
    @PreAuthorize("hasAuthority('CATALOG_VIEW')")
    public ResponseEntity<TestFullDTO> getFullTest(@PathVariable Long testId) {
        return new ResponseEntity<>(testBuilderService.getFull(testId), HttpStatus.OK);
    }

    @PostMapping("/full")
    @PreAuthorize("hasAuthority('CATALOG_CREATE')")
    public ResponseEntity<TestFullDTO> createFullTest(@Valid @RequestBody TestFullDTO dto) {
        return new ResponseEntity<>(testBuilderService.createFull(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{testId}/full")
    @PreAuthorize("hasAuthority('CATALOG_EDIT')")
    public ResponseEntity<TestFullDTO> updateFullTest(
            @Valid @RequestBody TestFullDTO dto,
            @PathVariable Long testId) {
        return new ResponseEntity<>(testBuilderService.updateFull(dto, testId), HttpStatus.OK);
    }
}
