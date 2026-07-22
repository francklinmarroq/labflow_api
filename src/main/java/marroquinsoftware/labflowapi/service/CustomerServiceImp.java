package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.Pathology;
import marroquinsoftware.labflowapi.payload.CustomerDTO;
import marroquinsoftware.labflowapi.payload.CustomerResponse;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.PathologyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CustomerServiceImp implements CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PathologyRepository pathologyRepository;

    @Override
    public CustomerResponse getAllCustomers(Integer pageNumber, Integer pageSize, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<Customer> page = customerRepository.findAll(pageable);
        List<CustomerDTO> dtos = page.getContent().stream().map(this::toDTO).toList();
        CustomerResponse response = new CustomerResponse();
        response.setContent(dtos);
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    @Override
    public CustomerDTO createCustomer(CustomerDTO dto) {
        if (dto.getNationalIdNumber() != null && customerRepository.findByNationalIdNumber(dto.getNationalIdNumber()) != null) {
            throw new APIException("Ya existe un paciente con el número de identificación '" + dto.getNationalIdNumber() + "'.");
        }
        if (dto.getTaxNumber() != null && customerRepository.findByTaxNumber(dto.getTaxNumber()) != null) {
            throw new APIException("Ya existe un paciente con el NIT '" + dto.getTaxNumber() + "'.");
        }
        Customer customer = new Customer();
        mapDtoToEntity(dto, customer);
        return toDTO(customerRepository.save(customer));
    }

    @Override
    public CustomerDTO updateCustomer(CustomerDTO dto, Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "customerId", id));
        if (dto.getNationalIdNumber() != null) {
            Customer existing = customerRepository.findByNationalIdNumber(dto.getNationalIdNumber());
            if (existing != null && !existing.getId().equals(id)) {
                throw new APIException("Ya existe un paciente con el número de identificación '" + dto.getNationalIdNumber() + "'.");
            }
        }
        if (dto.getTaxNumber() != null) {
            Customer existing = customerRepository.findByTaxNumber(dto.getTaxNumber());
            if (existing != null && !existing.getId().equals(id)) {
                throw new APIException("Ya existe un paciente con el NIT '" + dto.getTaxNumber() + "'.");
            }
        }
        mapDtoToEntity(dto, customer);
        return toDTO(customerRepository.save(customer));
    }

    @Override
    public CustomerDTO getCustomerById(Long id) {
        return toDTO(customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "customerId", id)));
    }

    @Override
    public CustomerDTO deleteCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "customerId", id));
        customerRepository.delete(customer);
        return toDTO(customer);
    }

    private void mapDtoToEntity(CustomerDTO dto, Customer customer) {
        customer.setName(dto.getName());
        customer.setAgeInDays(dto.getAgeInDays());
        customer.setSex(dto.getSex());
        customer.setNationalIdNumber(dto.getNationalIdNumber());
        customer.setTaxNumber(dto.getTaxNumber());
        customer.setPhone(dto.getPhone());
        customer.setEmail(dto.getEmail());
        customer.setPathologies(resolvePathologies(dto.getPathologyIds()));
    }

    private List<Pathology> resolvePathologies(List<Long> pathologyIds) {
        if (pathologyIds == null || pathologyIds.isEmpty()) {
            return new ArrayList<>();
        }
        return pathologyIds.stream()
                .map(pid -> pathologyRepository.findById(pid)
                        .orElseThrow(() -> new ResourceNotFoundException("Pathology", "pathologyId", pid)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private CustomerDTO toDTO(Customer customer) {
        CustomerDTO dto = new CustomerDTO();
        dto.setId(customer.getId());
        dto.setName(customer.getName());
        dto.setAgeInDays(customer.getAgeInDays());
        dto.setSex(customer.getSex());
        dto.setNationalIdNumber(customer.getNationalIdNumber());
        dto.setTaxNumber(customer.getTaxNumber());
        dto.setPhone(customer.getPhone());
        dto.setEmail(customer.getEmail());
        List<Long> pathologyIds = customer.getPathologies() != null
                ? customer.getPathologies().stream().map(Pathology::getId).toList()
                : Collections.emptyList();
        dto.setPathologyIds(pathologyIds);
        return dto;
    }
}
