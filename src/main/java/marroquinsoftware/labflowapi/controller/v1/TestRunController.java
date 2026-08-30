package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.payload.TestRunDTO;
import marroquinsoftware.labflowapi.service.TestRunService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    // El archivo sube por acá y no directo al bucket: así el bucket queda privado
    // sin CORS ni llaves en el navegador. La respuesta ya trae las URL firmadas.
    @PostMapping(value = "/{testId}/runs/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ORDERS_ENTER_RESULTS')")
    public ResponseEntity<TestRunDTO> addAttachmentRunToTest(@PathVariable Long testId, @RequestParam("files") List<MultipartFile> files) {
        return new ResponseEntity<>(testRunService.addAttachmentRunToTest(testId, files), HttpStatus.CREATED);
    }

    @DeleteMapping("/{testId}/runs/{runId}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('ORDERS_ENTER_RESULTS')")
    public ResponseEntity<TestRunDTO> deleteAttachment(@PathVariable Long testId, @PathVariable Long runId, @PathVariable Long attachmentId) {
        return new ResponseEntity<>(testRunService.deleteAttachment(testId, runId, attachmentId), HttpStatus.OK);
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
