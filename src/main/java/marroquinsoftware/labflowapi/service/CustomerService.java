package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.CustomerDTO;
import marroquinsoftware.labflowapi.payload.CustomerResponse;

public interface CustomerService {
    CustomerResponse getAllCustomers(Integer pageNumber, Integer pageSize, String sortBy, String sortDir);
    CustomerDTO createCustomer(CustomerDTO dto);
    CustomerDTO updateCustomer(CustomerDTO dto, Long id);
    CustomerDTO deleteCustomer(Long id);
}
