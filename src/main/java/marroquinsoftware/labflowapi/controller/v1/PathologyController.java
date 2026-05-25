package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.PathologyDTO;
import marroquinsoftware.labflowapi.payload.PathologyResponse;
import marroquinsoftware.labflowapi.service.PathologyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1/pathologies")
public class PathologyController {

    @Autowired
    private PathologyService pathologyService;

    @GetMapping
    public ResponseEntity<PathologyResponse> getAllPathologies(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_PATHOLOGIES_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        return new ResponseEntity<>(pathologyService.getAllPathologies(pageNumber, pageSize, sortBy, sortOrder), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<PathologyDTO> createPathology(@Valid @RequestBody PathologyDTO dto) {
        return new ResponseEntity<>(pathologyService.createPathology(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{pathologyId}")
    public ResponseEntity<PathologyDTO> updatePathology(@Valid @RequestBody PathologyDTO dto, @PathVariable Long pathologyId) {
        return new ResponseEntity<>(pathologyService.updatePathology(dto, pathologyId), HttpStatus.OK);
    }

    @DeleteMapping("/{pathologyId}")
    public ResponseEntity<PathologyDTO> deletePathology(@PathVariable Long pathologyId) {
        return new ResponseEntity<>(pathologyService.deletePathology(pathologyId), HttpStatus.OK);
    }
}
