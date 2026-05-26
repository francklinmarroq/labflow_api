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
    public ResponseEntity<LabOrderResponse> getAllOrders(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_ORDERS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        return new ResponseEntity<>(labOrderService.getAllOrders(pageNumber, pageSize, sortBy, sortOrder), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<LabOrderDTO> createOrder(@Valid @RequestBody LabOrderDTO dto) {
        return new ResponseEntity<>(labOrderService.createOrder(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<LabOrderDTO> updateOrder(@Valid @RequestBody LabOrderDTO dto, @PathVariable Long orderId) {
        return new ResponseEntity<>(labOrderService.updateOrder(dto, orderId), HttpStatus.OK);
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<LabOrderDTO> deleteOrder(@PathVariable Long orderId) {
        return new ResponseEntity<>(labOrderService.deleteOrder(orderId), HttpStatus.OK);
    }

    @GetMapping("/{orderId}/tests")
    public ResponseEntity<List<LabTestDTO>> getTestsByOrder(@PathVariable Long orderId) {
        return new ResponseEntity<>(labTestService.getTestsByOrder(orderId), HttpStatus.OK);
    }

    @PostMapping("/{orderId}/tests")
    public ResponseEntity<LabTestDTO> addTestToOrder(@PathVariable Long orderId, @Valid @RequestBody LabTestDTO dto) {
        return new ResponseEntity<>(labTestService.addTestToOrder(orderId, dto), HttpStatus.CREATED);
    }

    @PatchMapping("/{orderId}/tests/{labTestId}/assign")
    public ResponseEntity<LabTestDTO> assignTestConfig(
            @PathVariable Long orderId,
            @PathVariable Long labTestId,
            @RequestParam Long testConfigId) {
        return new ResponseEntity<>(labTestService.assignTestConfig(orderId, labTestId, testConfigId), HttpStatus.OK);
    }

    @DeleteMapping("/{orderId}/tests/{testId}")
    public ResponseEntity<LabTestDTO> removeTestFromOrder(@PathVariable Long orderId, @PathVariable Long testId) {
        return new ResponseEntity<>(labTestService.removeTestFromOrder(orderId, testId), HttpStatus.OK);
    }
}
