package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.payload.TestRunDTO;
import marroquinsoftware.labflowapi.service.TestRunService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1/tests")
public class TestRunController {

    @Autowired
    private TestRunService testRunService;

    @GetMapping("/{testId}/runs")
    public ResponseEntity<List<TestRunDTO>> getRunsByTest(@PathVariable Long testId) {
        return new ResponseEntity<>(testRunService.getRunsByTest(testId), HttpStatus.OK);
    }

    @PostMapping("/{testId}/runs")
    public ResponseEntity<TestRunDTO> addRunToTest(@PathVariable Long testId, @Valid @RequestBody TestRunDTO dto) {
        return new ResponseEntity<>(testRunService.addRunToTest(testId, dto), HttpStatus.CREATED);
    }

    @PutMapping("/{testId}/runs/{runId}/verify")
    public ResponseEntity<TestRunDTO> verifyRun(@PathVariable Long testId, @PathVariable Long runId) {
        return new ResponseEntity<>(testRunService.verifyRun(testId, runId), HttpStatus.OK);
    }

    @DeleteMapping("/{testId}/runs/{runId}")
    public ResponseEntity<TestRunDTO> deleteRun(@PathVariable Long testId, @PathVariable Long runId) {
        return new ResponseEntity<>(testRunService.deleteRun(testId, runId), HttpStatus.OK);
    }
}
