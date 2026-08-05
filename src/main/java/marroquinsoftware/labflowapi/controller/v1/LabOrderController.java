package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.payload.AnnulRequest;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.LabOrderResponse;
import marroquinsoftware.labflowapi.payload.LabTestDTO;
import marroquinsoftware.labflowapi.payload.TestRunDTO;
import marroquinsoftware.labflowapi.service.LabOrderService;
import marroquinsoftware.labflowapi.service.LabTestService;
import marroquinsoftware.labflowapi.service.TestRunService;
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

    @Autowired
    private TestRunService testRunService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ORDERS_VIEW','ORDERS_PRINT')")
    public ResponseEntity<LabOrderResponse> getAllOrders(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_ORDERS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder,
            // Filtro de estado opcional: sin él se listan las activas; con CANCELLED
            // se lista la pestaña de órdenes canceladas/archivadas.
            @RequestParam(required = false) OrderStatus status) {
        return new ResponseEntity<>(labOrderService.getAllOrders(pageNumber, pageSize, sortBy, sortOrder, status), HttpStatus.OK);
    }

    // Las pantallas de detalle/impresión solo necesitan una orden. Antes bajaban
    // el listado completo para hacer un find() en el cliente.
    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyAuthority('ORDERS_VIEW','ORDERS_PRINT','ORDERS_ENTER_RESULTS')")
    public ResponseEntity<LabOrderDTO> getOrderById(@PathVariable Long orderId) {
        return new ResponseEntity<>(labOrderService.getOrderById(orderId), HttpStatus.OK);
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

    // Cancelar (anular) la orden: borrado lógico a CANCELLED con motivo y auditoría.
    // Si la orden tiene factura viva, la anula en cascada (el servicio exige además
    // el permiso de anular facturas en ese caso).
    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('ORDERS_DELETE')")
    public ResponseEntity<LabOrderDTO> cancelOrder(@PathVariable Long orderId, @Valid @RequestBody AnnulRequest request) {
        return new ResponseEntity<>(labOrderService.cancelOrder(orderId, request.getReason()), HttpStatus.OK);
    }

    @GetMapping("/{orderId}/tests")
    @PreAuthorize("hasAnyAuthority('ORDERS_VIEW','ORDERS_PRINT','ORDERS_ENTER_RESULTS')")
    public ResponseEntity<List<LabTestDTO>> getTestsByOrder(@PathVariable Long orderId) {
        return new ResponseEntity<>(labTestService.getTestsByOrder(orderId), HttpStatus.OK);
    }

    // Corridas de resultados de todos los exámenes de la orden en una sola llamada.
    // El detalle y la impresión antes pedían GET /tests/{id}/runs una vez por examen
    // (N llamadas); esto lo colapsa en un único request. Mismos DTOs (cada uno con su
    // testId), el cliente agrupa por examen.
    // Todas las corridas de la orden en una sola llamada. El detalle de orden hacía
    // GET /tests/{id}/runs una vez por examen (N requests); esto las colapsa en 1.
    @GetMapping("/{orderId}/runs")
    @PreAuthorize("hasAnyAuthority('ORDERS_VIEW','ORDERS_PRINT','ORDERS_ENTER_RESULTS')")
    public ResponseEntity<List<TestRunDTO>> getRunsByOrder(@PathVariable Long orderId) {
        return new ResponseEntity<>(testRunService.getRunsByOrder(orderId), HttpStatus.OK);
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

    @PatchMapping("/{orderId}/tests/{labTestId}/method")
    @PreAuthorize("hasAnyAuthority('ORDERS_CREATE','ORDERS_ENTER_RESULTS')")
    public ResponseEntity<LabTestDTO> updateTestMethod(
            @PathVariable Long orderId,
            @PathVariable Long labTestId,
            @RequestBody LabTestDTO dto) {
        return new ResponseEntity<>(labTestService.updateMethod(orderId, labTestId, dto.getMethod()), HttpStatus.OK);
    }

    @DeleteMapping("/{orderId}/tests/{testId}")
    @PreAuthorize("hasAnyAuthority('ORDERS_CREATE','ORDERS_DELETE')")
    public ResponseEntity<LabTestDTO> removeTestFromOrder(@PathVariable Long orderId, @PathVariable Long testId) {
        return new ResponseEntity<>(labTestService.removeTestFromOrder(orderId, testId), HttpStatus.OK);
    }
}
