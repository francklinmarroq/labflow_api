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

    // Lectura del catálogo: también la necesitan las vistas de órdenes (crear,
    // ver, ingresar resultados e imprimir usan los listados del catálogo).
    @GetMapping
    @PreAuthorize("hasAnyAuthority('CATALOG_VIEW','ORDERS_VIEW','ORDERS_CREATE','ORDERS_ENTER_RESULTS','ORDERS_PRINT')")
    public ResponseEntity<UnitResponse> getAllUnits(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_UNITS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        UnitResponse unitResponse = unitService.getAllUnits(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(unitResponse, HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_CREATE')")
    public ResponseEntity<UnitDTO> createUnit(@Valid @RequestBody UnitDTO unitDTO) {
        return new ResponseEntity<UnitDTO>(unitService.createUnit(unitDTO), HttpStatus.CREATED);
    }

    @PutMapping("/{unitId}")
    @PreAuthorize("hasAuthority('CATALOG_EDIT')")
    public ResponseEntity<UnitDTO> updateUnit(@Valid @RequestBody UnitDTO unitDTO, @PathVariable Long unitId) {
        UnitDTO savedUnit = unitService.updateUnit(unitDTO, unitId);
        return new ResponseEntity<>(savedUnit, HttpStatus.OK);

    }

    @DeleteMapping("/{unitId}")
    @PreAuthorize("hasAuthority('CATALOG_DELETE')")
    public ResponseEntity<UnitDTO> deleteUnit(@PathVariable Long unitId) {
        UnitDTO status = unitService.deleteUnit(unitId);
        return new ResponseEntity<>(status, HttpStatus.OK);
    }

}
