package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.CustomerDTO;
import marroquinsoftware.labflowapi.payload.CustomerResponse;
import marroquinsoftware.labflowapi.payload.PatientTestHistoryDTO;
import marroquinsoftware.labflowapi.service.CustomerService;
import marroquinsoftware.labflowapi.service.PatientHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private PatientHistoryService patientHistoryService;

    // Las vistas de órdenes también leen pacientes (crear orden, imprimir).
    @GetMapping
    @PreAuthorize("hasAnyAuthority('PATIENTS_VIEW','ORDERS_VIEW','ORDERS_CREATE','ORDERS_PRINT')")
    public ResponseEntity<CustomerResponse> getAllCustomers(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_CUSTOMERS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        return new ResponseEntity<>(customerService.getAllCustomers(pageNumber, pageSize, sortBy, sortOrder), HttpStatus.OK);
    }

    // Al crear una orden se puede registrar al paciente en el mismo flujo.
    @PostMapping
    @PreAuthorize("hasAnyAuthority('PATIENTS_CREATE','ORDERS_CREATE')")
    public ResponseEntity<CustomerDTO> createCustomer(@Valid @RequestBody CustomerDTO dto) {
        return new ResponseEntity<>(customerService.createCustomer(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{customerId}")
    @PreAuthorize("hasAuthority('PATIENTS_EDIT')")
    public ResponseEntity<CustomerDTO> updateCustomer(@Valid @RequestBody CustomerDTO dto, @PathVariable Long customerId) {
        return new ResponseEntity<>(customerService.updateCustomer(dto, customerId), HttpStatus.OK);
    }

    @DeleteMapping("/{customerId}")
    @PreAuthorize("hasAuthority('PATIENTS_DELETE')")
    public ResponseEntity<CustomerDTO> deleteCustomer(@PathVariable Long customerId) {
        return new ResponseEntity<>(customerService.deleteCustomer(customerId), HttpStatus.OK);
    }

    @GetMapping("/{customerId}/history")
    @PreAuthorize("hasAuthority('PATIENTS_VIEW')")
    public ResponseEntity<List<PatientTestHistoryDTO>> getPatientHistory(
            @PathVariable Long customerId,
            @RequestParam(required = false) String testName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo) {
        return new ResponseEntity<>(patientHistoryService.getPatientHistory(customerId, testName, dateFrom, dateTo), HttpStatus.OK);
    }
}
