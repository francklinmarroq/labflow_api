package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.SupplierActiveRequest;
import marroquinsoftware.labflowapi.payload.SupplierDTO;
import marroquinsoftware.labflowapi.payload.SupplierResponse;
import marroquinsoftware.labflowapi.service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    @Autowired
    private SupplierService supplierService;

    // Quien registra compras o consulta cuentas por pagar necesita elegir y ver
    // proveedores sin que eso le dé la administración del catálogo.
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SUPPLIERS_VIEW','SUPPLIERS_MANAGE','PURCHASES_VIEW','PURCHASES_MANAGE')")
    public ResponseEntity<SupplierResponse> getSuppliers(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_SUPPLIERS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active) {
        return new ResponseEntity<>(
                supplierService.getSuppliers(pageNumber, pageSize, sortBy, sortOrder, search, active), HttpStatus.OK);
    }

    @GetMapping("/{supplierId}")
    @PreAuthorize("hasAnyAuthority('SUPPLIERS_VIEW','SUPPLIERS_MANAGE','PURCHASES_VIEW','PURCHASES_MANAGE')")
    public ResponseEntity<SupplierDTO> getSupplier(@PathVariable Long supplierId) {
        return new ResponseEntity<>(supplierService.getSupplier(supplierId), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SUPPLIERS_MANAGE')")
    public ResponseEntity<SupplierDTO> createSupplier(@Valid @RequestBody SupplierDTO dto) {
        return new ResponseEntity<>(supplierService.createSupplier(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{supplierId}")
    @PreAuthorize("hasAuthority('SUPPLIERS_MANAGE')")
    public ResponseEntity<SupplierDTO> updateSupplier(@PathVariable Long supplierId,
                                                      @Valid @RequestBody SupplierDTO dto) {
        return new ResponseEntity<>(supplierService.updateSupplier(supplierId, dto), HttpStatus.OK);
    }

    // Los proveedores no se borran: tienen compras y pagos que los apuntan.
    @PutMapping("/{supplierId}/active")
    @PreAuthorize("hasAuthority('SUPPLIERS_MANAGE')")
    public ResponseEntity<SupplierDTO> setActive(@PathVariable Long supplierId,
                                                 @Valid @RequestBody SupplierActiveRequest request) {
        return new ResponseEntity<>(supplierService.setActive(supplierId, request.getActive()), HttpStatus.OK);
    }
}
