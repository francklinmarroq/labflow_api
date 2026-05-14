package marroquinsoftware.labflowapi.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import marroquinsoftware.labflowapi.model.Unit;
import marroquinsoftware.labflowapi.service.UnitService;

@RestController
public class UnitController {
    @Autowired
    private UnitService unitService;

    @GetMapping("/api/v1/public/units")
    public ResponseEntity<List<Unit>> getAllUnits() {
        List<Unit> units = unitService.getAllUnits();
        return new ResponseEntity<>(units, HttpStatus.CREATED);
    }

    @PostMapping("/api/v1/public/units")
    public ResponseEntity<String> createUnit(@RequestBody Unit unit) {
        unitService.createUnit(unit);
        return new ResponseEntity<String>("Unit addded successfully.", HttpStatus.OK);
    }

    @DeleteMapping("/api/v1/public/units/{unitId}")
    public ResponseEntity<String> deleteUnit(@PathVariable Long unitId) {
        try {
            String status = unitService.deleteUnit(unitId);
            return new ResponseEntity<>(status, HttpStatus.OK);
        } catch (ResponseStatusException e) {
            return new ResponseEntity<>(e.getReason(), e.getStatusCode());
        }
    }

    @PutMapping("/api/v1/public/units/{unitId}")
    public ResponseEntity<String> updateUnit(@RequestBody Unit unit, @PathVariable Long unitId) {
        try {
            Unit savedUnit = unitService.updateUnit(unit, unitId);
            return new ResponseEntity<>("Category with id " + unitId, HttpStatus.OK);
        } catch (ResponseStatusException e) {
            return new ResponseEntity<>(e.getReason(), e.getStatusCode());

        }
    }
}
