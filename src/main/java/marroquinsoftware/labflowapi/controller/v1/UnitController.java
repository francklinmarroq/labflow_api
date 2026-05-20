package marroquinsoftware.labflowapi.controller.v1;

import java.util.List;

import jakarta.validation.Valid;
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
    public ResponseEntity<UnitResponse> getAllUnits() {
        UnitResponse unitResponse = unitService.getAllUnits();
        return new ResponseEntity<>(unitResponse, HttpStatus.OK);
    }

    @PostMapping("/public/units")
    public ResponseEntity<String> createUnit(@Valid @RequestBody Unit unit) {
        unitService.createUnit(unit);
        return new ResponseEntity<String>("Unit addded successfully.", HttpStatus.CREATED);
    }

    @DeleteMapping("/public/units/{unitId}")
    public ResponseEntity<String> deleteUnit(@PathVariable Long unitId) {
        String status = unitService.deleteUnit(unitId);
        return new ResponseEntity<>(status, HttpStatus.OK);
    }

    @PutMapping("/public/units/{unitId}")
    public ResponseEntity<String> updateUnit(@Valid @RequestBody Unit unit, @PathVariable Long unitId) {
        Unit savedUnit = unitService.updateUnit(unit, unitId);
        return new ResponseEntity<>("Category with id " + unitId, HttpStatus.OK);

    }
}
