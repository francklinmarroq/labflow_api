package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.model.Sex;
import marroquinsoftware.labflowapi.payload.ReferenceRangeDTO;
import marroquinsoftware.labflowapi.payload.ReferenceRangeResponse;
import marroquinsoftware.labflowapi.service.ReferenceRangeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/parameters/{parameterId}/reference-ranges")
public class ReferenceRangeController {

    @Autowired
    private ReferenceRangeService referenceRangeService;

    @GetMapping
    public ResponseEntity<ReferenceRangeResponse> getRangesByParameter(
            @PathVariable Long parameterId,
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_REFERENCE_RANGES_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        return new ResponseEntity<>(
                referenceRangeService.getRangesByParameter(parameterId, pageNumber, pageSize, sortBy, sortOrder),
                HttpStatus.OK);
    }

    @GetMapping("/applicable")
    public ResponseEntity<List<ReferenceRangeDTO>> findApplicable(
            @PathVariable Long parameterId,
            @RequestParam Sex sex,
            @RequestParam Integer ageDays) {
        return new ResponseEntity<>(
                referenceRangeService.findApplicable(parameterId, sex, ageDays),
                HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ReferenceRangeDTO> createReferenceRange(
            @PathVariable Long parameterId,
            @Valid @RequestBody ReferenceRangeDTO dto) {
        return new ResponseEntity<>(
                referenceRangeService.createReferenceRange(parameterId, dto),
                HttpStatus.CREATED);
    }

    @PutMapping("/{rangeId}")
    public ResponseEntity<ReferenceRangeDTO> updateReferenceRange(
            @PathVariable Long parameterId,
            @PathVariable Long rangeId,
            @Valid @RequestBody ReferenceRangeDTO dto) {
        return new ResponseEntity<>(
                referenceRangeService.updateReferenceRange(parameterId, rangeId, dto),
                HttpStatus.OK);
    }

    @DeleteMapping("/{rangeId}")
    public ResponseEntity<ReferenceRangeDTO> deleteReferenceRange(
            @PathVariable Long parameterId,
            @PathVariable Long rangeId) {
        return new ResponseEntity<>(
                referenceRangeService.deleteReferenceRange(parameterId, rangeId),
                HttpStatus.OK);
    }
}
