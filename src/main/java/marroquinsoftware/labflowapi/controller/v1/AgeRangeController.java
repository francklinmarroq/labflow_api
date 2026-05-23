package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.AgeRangeDTO;
import marroquinsoftware.labflowapi.payload.AgeRangeResponse;
import marroquinsoftware.labflowapi.service.AgeRangeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/age-ranges")
public class AgeRangeController {

    @Autowired
    private AgeRangeService ageRangeService;

    @GetMapping
    public ResponseEntity<AgeRangeResponse> getAllAgeRanges(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_AGE_RANGES_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        return new ResponseEntity<>(
                ageRangeService.getAllAgeRanges(pageNumber, pageSize, sortBy, sortOrder),
                HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<AgeRangeDTO> createAgeRange(@Valid @RequestBody AgeRangeDTO dto) {
        return new ResponseEntity<>(ageRangeService.createAgeRange(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{ageRangeId}")
    public ResponseEntity<AgeRangeDTO> updateAgeRange(
            @Valid @RequestBody AgeRangeDTO dto,
            @PathVariable Long ageRangeId) {
        return new ResponseEntity<>(ageRangeService.updateAgeRange(dto, ageRangeId), HttpStatus.OK);
    }

    @DeleteMapping("/{ageRangeId}")
    public ResponseEntity<AgeRangeDTO> deleteAgeRange(@PathVariable Long ageRangeId) {
        return new ResponseEntity<>(ageRangeService.deleteAgeRange(ageRangeId), HttpStatus.OK);
    }
}
