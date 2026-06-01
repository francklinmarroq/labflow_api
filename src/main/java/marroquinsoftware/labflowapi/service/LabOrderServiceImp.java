package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.LabOrderResponse;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class LabOrderServiceImp implements LabOrderService {

    @Autowired
    private LabOrderRepository labOrderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Override
    public LabOrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<LabOrder> page = labOrderRepository.findAll(pageable);
        List<LabOrderDTO> dtos = page.getContent().stream().map(this::toDTO).toList();
        LabOrderResponse response = new LabOrderResponse();
        response.setContent(dtos);
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    @Override
    public LabOrderDTO createOrder(LabOrderDTO dto) {
        Customer customer = customerRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "customerId", dto.getCustomerId()));
        LabOrder order = new LabOrder();
        order.setCustomer(customer);
        order.setRequestedAt(dto.getRequestedAt() != null ? dto.getRequestedAt() : Instant.now());
        order.setStatus(dto.getStatus() != null ? dto.getStatus() : OrderStatus.PENDING);
        order.setNotes(dto.getNotes());
        return toDTO(labOrderRepository.save(order));
    }

    @Override
    public LabOrderDTO updateOrder(LabOrderDTO dto, Long id) {
        LabOrder order = labOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LabOrder", "orderId", id));
        if (dto.getCustomerId() != null) {
            Customer customer = customerRepository.findById(dto.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "customerId", dto.getCustomerId()));
            order.setCustomer(customer);
        }
        if (dto.getRequestedAt() != null) order.setRequestedAt(dto.getRequestedAt());
        if (dto.getStatus() != null) order.setStatus(dto.getStatus());
        order.setNotes(dto.getNotes());
        return toDTO(labOrderRepository.save(order));
    }

    @Override
    public LabOrderDTO deleteOrder(Long id) {
        LabOrder order = labOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LabOrder", "orderId", id));
        labOrderRepository.delete(order);
        return toDTO(order);
    }

    private LabOrderDTO toDTO(LabOrder order) {
        LabOrderDTO dto = new LabOrderDTO();
        dto.setId(order.getId());
        dto.setCustomerId(order.getCustomer().getId());
        dto.setRequestedAt(order.getRequestedAt());
        dto.setStatus(order.getStatus());
        dto.setNotes(order.getNotes());
        return dto;
    }
}
