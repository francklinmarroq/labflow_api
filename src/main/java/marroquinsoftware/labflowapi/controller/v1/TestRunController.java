package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.payload.TestRunDTO;
import marroquinsoftware.labflowapi.service.TestRunService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tests")
public class TestRunController {

    @Autowired
    private TestRunService testRunService;

    @GetMapping("/{testId}/runs")
    @PreAuthorize("hasAnyAuthority('ORDERS_VIEW','ORDERS_PRINT','ORDERS_ENTER_RESULTS')")
    public ResponseEntity<List<TestRunDTO>> getRunsByTest(@PathVariable Long testId) {
        return new ResponseEntity<>(testRunService.getRunsByTest(testId), HttpStatus.OK);
    }

    @PostMapping("/{testId}/runs")
    @PreAuthorize("hasAuthority('ORDERS_ENTER_RESULTS')")
    public ResponseEntity<TestRunDTO> addRunToTest(@PathVariable Long testId, @Valid @RequestBody TestRunDTO dto) {
        return new ResponseEntity<>(testRunService.addRunToTest(testId, dto), HttpStatus.CREATED);
    }

    @PutMapping("/{testId}/runs/{runId}/verify")
    @PreAuthorize("hasAuthority('ORDERS_ENTER_RESULTS')")
    public ResponseEntity<TestRunDTO> verifyRun(@PathVariable Long testId, @PathVariable Long runId) {
        return new ResponseEntity<>(testRunService.verifyRun(testId, runId), HttpStatus.OK);
    }

    @DeleteMapping("/{testId}/runs/{runId}")
    @PreAuthorize("hasAuthority('ORDERS_ENTER_RESULTS')")
    public ResponseEntity<TestRunDTO> deleteRun(@PathVariable Long testId, @PathVariable Long runId) {
        return new ResponseEntity<>(testRunService.deleteRun(testId, runId), HttpStatus.OK);
    }
}
