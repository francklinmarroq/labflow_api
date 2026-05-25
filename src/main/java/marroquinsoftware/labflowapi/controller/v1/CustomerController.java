package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.CustomerDTO;
import marroquinsoftware.labflowapi.payload.CustomerResponse;
import marroquinsoftware.labflowapi.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @GetMapping
    public ResponseEntity<CustomerResponse> getAllCustomers(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_CUSTOMERS_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        return new ResponseEntity<>(customerService.getAllCustomers(pageNumber, pageSize, sortBy, sortOrder), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<CustomerDTO> createCustomer(@Valid @RequestBody CustomerDTO dto) {
        return new ResponseEntity<>(customerService.createCustomer(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{customerId}")
    public ResponseEntity<CustomerDTO> updateCustomer(@Valid @RequestBody CustomerDTO dto, @PathVariable Long customerId) {
        return new ResponseEntity<>(customerService.updateCustomer(dto, customerId), HttpStatus.OK);
    }

    @DeleteMapping("/{customerId}")
    public ResponseEntity<CustomerDTO> deleteCustomer(@PathVariable Long customerId) {
        return new ResponseEntity<>(customerService.deleteCustomer(customerId), HttpStatus.OK);
    }
}
