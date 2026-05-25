package marroquinsoftware.labflowapi.controller.v1;

import marroquinsoftware.labflowapi.payload.TestResultDTO;
import marroquinsoftware.labflowapi.service.TestResultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1/runs")
public class TestResultController {

    @Autowired
    private TestResultService testResultService;

    @GetMapping("/{runId}/results")
    public ResponseEntity<List<TestResultDTO>> getResultsByRun(@PathVariable Long runId) {
        return new ResponseEntity<>(testResultService.getResultsByRun(runId), HttpStatus.OK);
    }

    @PutMapping("/{runId}/results/{resultId}")
    public ResponseEntity<TestResultDTO> updateResult(
            @PathVariable Long runId,
            @PathVariable Long resultId,
            @RequestBody TestResultDTO dto) {
        return new ResponseEntity<>(testResultService.updateResult(runId, resultId, dto), HttpStatus.OK);
    }

    @DeleteMapping("/{runId}/results/{resultId}")
    public ResponseEntity<TestResultDTO> deleteResult(@PathVariable Long runId, @PathVariable Long resultId) {
        return new ResponseEntity<>(testResultService.deleteResult(runId, resultId), HttpStatus.OK);
    }
}
