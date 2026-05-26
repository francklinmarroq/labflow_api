package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.ParameterDTO;
import marroquinsoftware.labflowapi.payload.ParameterResponse;
import marroquinsoftware.labflowapi.service.ParameterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/parameters")
public class ParameterController {

    @Autowired
    private ParameterService parameterService;

    @GetMapping
    public ResponseEntity<ParameterResponse> getAllParameters(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_PARAMETERS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        ParameterResponse parameterResponse = parameterService.getAllParameters(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(parameterResponse, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ParameterDTO> createParameter(@Valid @RequestBody ParameterDTO parameterDTO) {
        return new ResponseEntity<>(parameterService.createParameter(parameterDTO), HttpStatus.CREATED);
    }

    @PutMapping("/{parameterId}")
    public ResponseEntity<ParameterDTO> updateParameter(@Valid @RequestBody ParameterDTO parameterDTO, @PathVariable Long parameterId) {
        ParameterDTO savedParameter = parameterService.updateParameter(parameterDTO, parameterId);
        return new ResponseEntity<>(savedParameter, HttpStatus.OK);
    }

    @DeleteMapping("/{parameterId}")
    public ResponseEntity<ParameterDTO> deleteParameter(@PathVariable Long parameterId) {
        ParameterDTO deleted = parameterService.deleteParameter(parameterId);
        return new ResponseEntity<>(deleted, HttpStatus.OK);
    }
}
