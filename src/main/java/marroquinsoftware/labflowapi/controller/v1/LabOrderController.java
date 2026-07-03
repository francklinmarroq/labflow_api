package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.LabOrderResponse;
import marroquinsoftware.labflowapi.payload.LabTestDTO;
import marroquinsoftware.labflowapi.service.LabOrderService;
import marroquinsoftware.labflowapi.service.LabTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class LabOrderController {

    @Autowired
    private LabOrderService labOrderService;

    @Autowired
    private LabTestService labTestService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ORDERS_VIEW','ORDERS_PRINT')")
    public ResponseEntity<LabOrderResponse> getAllOrders(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_ORDERS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        return new ResponseEntity<>(labOrderService.getAllOrders(pageNumber, pageSize, sortBy, sortOrder), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORDERS_CREATE')")
    public ResponseEntity<LabOrderDTO> createOrder(@Valid @RequestBody LabOrderDTO dto) {
        return new ResponseEntity<>(labOrderService.createOrder(dto), HttpStatus.CREATED);
    }

    // Actualizar la orden incluye los cambios de estado, que también ocurren
    // durante el flujo de resultados (en proceso, listos, verificada...).
    @PutMapping("/{orderId}")
    @PreAuthorize("hasAnyAuthority('ORDERS_CREATE','ORDERS_ENTER_RESULTS')")
    public ResponseEntity<LabOrderDTO> updateOrder(@Valid @RequestBody LabOrderDTO dto, @PathVariable Long orderId) {
        return new ResponseEntity<>(labOrderService.updateOrder(dto, orderId), HttpStatus.OK);
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasAuthority('ORDERS_DELETE')")
    public ResponseEntity<LabOrderDTO> deleteOrder(@PathVariable Long orderId) {
        return new ResponseEntity<>(labOrderService.deleteOrder(orderId), HttpStatus.OK);
    }

    @GetMapping("/{orderId}/tests")
    @PreAuthorize("hasAnyAuthority('ORDERS_VIEW','ORDERS_PRINT','ORDERS_ENTER_RESULTS')")
    public ResponseEntity<List<LabTestDTO>> getTestsByOrder(@PathVariable Long orderId) {
        return new ResponseEntity<>(labTestService.getTestsByOrder(orderId), HttpStatus.OK);
    }

    @PostMapping("/{orderId}/tests")
    @PreAuthorize("hasAuthority('ORDERS_CREATE')")
    public ResponseEntity<LabTestDTO> addTestToOrder(@PathVariable Long orderId, @Valid @RequestBody LabTestDTO dto) {
        return new ResponseEntity<>(labTestService.addTestToOrder(orderId, dto), HttpStatus.CREATED);
    }

    // Asignar perfil y editar notas/muestra ocurre tanto al armar la orden
    // como al ingresar resultados.
    @PatchMapping("/{orderId}/tests/{labTestId}/assign")
    @PreAuthorize("hasAnyAuthority('ORDERS_CREATE','ORDERS_ENTER_RESULTS')")
    public ResponseEntity<LabTestDTO> assignTestConfig(
            @PathVariable Long orderId,
            @PathVariable Long labTestId,
            @RequestParam Long testConfigId) {
        return new ResponseEntity<>(labTestService.assignTestConfig(orderId, labTestId, testConfigId), HttpStatus.OK);
    }

    @PatchMapping("/{orderId}/tests/{labTestId}/notes")
    @PreAuthorize("hasAnyAuthority('ORDERS_CREATE','ORDERS_ENTER_RESULTS')")
    public ResponseEntity<LabTestDTO> updateTestNotes(
            @PathVariable Long orderId,
            @PathVariable Long labTestId,
            @RequestBody LabTestDTO dto) {
        return new ResponseEntity<>(labTestService.updateNotes(orderId, labTestId, dto.getNotes()), HttpStatus.OK);
    }

    @PatchMapping("/{orderId}/tests/{labTestId}/sample-type")
    @PreAuthorize("hasAnyAuthority('ORDERS_CREATE','ORDERS_ENTER_RESULTS')")
    public ResponseEntity<LabTestDTO> updateTestSampleType(
            @PathVariable Long orderId,
            @PathVariable Long labTestId,
            @RequestBody LabTestDTO dto) {
        return new ResponseEntity<>(labTestService.updateSampleType(orderId, labTestId, dto.getSampleType()), HttpStatus.OK);
    }

    @DeleteMapping("/{orderId}/tests/{testId}")
    @PreAuthorize("hasAnyAuthority('ORDERS_CREATE','ORDERS_DELETE')")
    public ResponseEntity<LabTestDTO> removeTestFromOrder(@PathVariable Long orderId, @PathVariable Long testId) {
        return new ResponseEntity<>(labTestService.removeTestFromOrder(orderId, testId), HttpStatus.OK);
    }
}
