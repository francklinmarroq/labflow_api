package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.payload.AnnulRequest;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.LabOrderResponse;
import marroquinsoftware.labflowapi.payload.LabTestDTO;
import marroquinsoftware.labflowapi.payload.OrderExportRequest;
import marroquinsoftware.labflowapi.payload.OrderExportRowDTO;
import marroquinsoftware.labflowapi.payload.TestRunDTO;
import marroquinsoftware.labflowapi.payload.UninvoicedOrderResponse;
import marroquinsoftware.labflowapi.service.InvoiceService;
import marroquinsoftware.labflowapi.service.LabOrderService;
import marroquinsoftware.labflowapi.service.LabTestService;
import marroquinsoftware.labflowapi.service.ReferralService;
import marroquinsoftware.labflowapi.service.TestRunService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class LabOrderController {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private LabOrderService labOrderService;

    @Autowired
    private LabTestService labTestService;

    @Autowired
    private TestRunService testRunService;

    @Autowired
    private ReferralService referralService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ORDERS_VIEW','ORDERS_PRINT')")
    public ResponseEntity<LabOrderResponse> getAllOrders(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_ORDERS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder,
            // Filtro de estado opcional: sin él se listan las activas; con CANCELLED
            // se lista la pestaña de órdenes canceladas/archivadas.
            @RequestParam(required = false) OrderStatus status,
            // Filtro por etiqueta (convenio, campaña, empresa…): solo las órdenes
            // marcadas con esa etiqueta.
            @RequestParam(required = false) Long tagId) {
        return new ResponseEntity<>(
                labOrderService.getAllOrders(pageNumber, pageSize, sortBy, sortOrder, status, tagId),
                HttpStatus.OK);
    }

    // Órdenes pendientes de facturar, para armar una factura de una o varias.
    // Va antes de /{orderId} solo por legibilidad: Spring prefiere la ruta literal.
    @GetMapping("/uninvoiced")
    @PreAuthorize("hasAuthority('INVOICES_CREATE')")
    public ResponseEntity<UninvoicedOrderResponse> getUninvoicedOrders(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ResponseEntity<>(
                invoiceService.getUninvoicedOrders(pageNumber, pageSize, customerId, from, to), HttpStatus.OK);
    }

    // Exámenes y total a pagar de las órdenes filtradas en la pantalla, para
    // exportarlas a Excel. Es POST porque la lista de ids puede no caber en una URL.
    @PostMapping("/export")
    @PreAuthorize("hasAuthority('ORDERS_VIEW')")
    public ResponseEntity<List<OrderExportRowDTO>> exportOrders(@Valid @RequestBody OrderExportRequest request) {
        return new ResponseEntity<>(labOrderService.exportOrders(request.getOrderIds()), HttpStatus.OK);
    }

    // Las pantallas de detalle/impresión solo necesitan una orden. Antes bajaban
    // el listado completo para hacer un find() en el cliente.
    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyAuthority('ORDERS_VIEW','ORDERS_PRINT','ORDERS_ENTER_RESULTS')")
    public ResponseEntity<LabOrderDTO> getOrderById(@PathVariable Long orderId, Authentication authentication) {
        LabOrderDTO dto = labOrderService.getOrderById(orderId);
        // Las remisiones de la orden se embeben en el detalle para ahorrarle al front
        // una 2.ª llamada a GET /orders/{id}/referrals: una invocación menos de
        // Worker→Durable Object→contenedor (piso de ~0.7 s) por apertura del detalle,
        // la pantalla de más tráfico del sistema. Reusa referralService.getReferralsByOrder,
        // así que trae EXACTAMENTE las mismas filas y el mismo orden que ese endpoint.
        //
        // Se embeben SOLO si el usuario tiene la misma autoridad que exige ese endpoint
        // (ORDERS_VIEW o ORDERS_REFER): este detalle también lo pueden abrir roles con
        // solo ORDERS_PRINT/ORDERS_ENTER_RESULTS, y a esos no se les deben exponer las
        // remisiones (hoy su llamada al endpoint daría 403). Si no están autorizados,
        // `referrals` queda en null y el front cae a su llamada —ya condicionada por el
        // mismo permiso—, de modo que quién ve qué no cambia.
        if (hasAnyAuthority(authentication, "ORDERS_VIEW", "ORDERS_REFER")) {
            dto.setReferrals(referralService.getReferralsByOrder(orderId));
        }
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    /** ¿El usuario autenticado tiene alguna de las autoridades indicadas? */
    private static boolean hasAnyAuthority(Authentication authentication, String... required) {
        if (authentication == null || authentication.getAuthorities() == null) return false;
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            for (String authority : required) {
                if (authority.equals(granted.getAuthority())) return true;
            }
        }
        return false;
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
