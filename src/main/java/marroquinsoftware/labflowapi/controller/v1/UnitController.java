package marroquinsoftware.labflowapi.controller.v1;

import java.util.List;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.UnitDTO;
import marroquinsoftware.labflowapi.payload.UnitResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import marroquinsoftware.labflowapi.model.Unit;
import marroquinsoftware.labflowapi.service.UnitService;

@RestController
@RequestMapping("/api/v1")
public class UnitController {
    @Autowired
    private UnitService unitService;

    @GetMapping("/public/units")
    public ResponseEntity<UnitResponse> getAllUnits(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_UNITS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        UnitResponse unitResponse = unitService.getAllUnits(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(unitResponse, HttpStatus.OK);
    }

    @PostMapping("/public/units")
    public ResponseEntity<UnitDTO> createUnit(@Valid @RequestBody UnitDTO unitDTO) {
        return new ResponseEntity<UnitDTO>(unitService.createUnit(unitDTO), HttpStatus.CREATED);
    }

    @PutMapping("/public/units/{unitId}")
    public ResponseEntity<UnitDTO> updateUnit(@Valid @RequestBody UnitDTO unitDTO, @PathVariable Long unitId) {
        UnitDTO savedUnit = unitService.updateUnit(unitDTO, unitId);
        return new ResponseEntity<>(savedUnit, HttpStatus.OK);

    }

    @DeleteMapping("/public/units/{unitId}")
    public ResponseEntity<UnitDTO> deleteUnit(@PathVariable Long unitId) {
        UnitDTO status = unitService.deleteUnit(unitId);
        return new ResponseEntity<>(status, HttpStatus.OK);
    }

}
