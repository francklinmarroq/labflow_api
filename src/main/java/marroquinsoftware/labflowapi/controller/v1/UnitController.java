package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.UnitDTO;
import marroquinsoftware.labflowapi.payload.UnitResponse;
import marroquinsoftware.labflowapi.service.UnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/units")
public class UnitController {
    @Autowired
    private UnitService unitService;

    @GetMapping
    public ResponseEntity<UnitResponse> getAllUnits(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_UNITS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        UnitResponse unitResponse = unitService.getAllUnits(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(unitResponse, HttpStatus.OK);
    }

    @PreAuthorize("hasRole('user')")
    @PostMapping
    public ResponseEntity<UnitDTO> createUnit(@Valid @RequestBody UnitDTO unitDTO) {
        return new ResponseEntity<UnitDTO>(unitService.createUnit(unitDTO), HttpStatus.CREATED);
    }

    @PutMapping("/{unitId}")
    public ResponseEntity<UnitDTO> updateUnit(@Valid @RequestBody UnitDTO unitDTO, @PathVariable Long unitId) {
        UnitDTO savedUnit = unitService.updateUnit(unitDTO, unitId);
        return new ResponseEntity<>(savedUnit, HttpStatus.OK);

    }

    @DeleteMapping("/{unitId}")
    public ResponseEntity<UnitDTO> deleteUnit(@PathVariable Long unitId) {
        UnitDTO status = unitService.deleteUnit(unitId);
        return new ResponseEntity<>(status, HttpStatus.OK);
    }

}
