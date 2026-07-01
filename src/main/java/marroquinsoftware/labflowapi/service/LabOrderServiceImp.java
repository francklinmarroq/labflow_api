package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.LabOrderCounter;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.LabOrderResponse;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderCounterRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class LabOrderServiceImp implements LabOrderService {

    @Autowired
    private LabOrderRepository labOrderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private LabOrderCounterRepository labOrderCounterRepository;

    @Override
    public LabOrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortDir) {
        Long laboratoryId = requireLaboratoryId();
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<LabOrder> page = labOrderRepository
                .findByLaboratory_IdAndStatusNot(laboratoryId, OrderStatus.CANCELLED, pageable);
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
    @Transactional
    public LabOrderDTO createOrder(LabOrderDTO dto) {
        Long laboratoryId = requireLaboratoryId();
        Laboratory laboratory = laboratoryRepository.findById(laboratoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", "laboratoryId", laboratoryId));
        Customer customer = customerRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "customerId", dto.getCustomerId()));
        LabOrder order = new LabOrder();
        order.setLaboratory(laboratory);
        order.setOrderNumber(nextOrderNumber(laboratoryId));
        order.setCustomer(customer);
        order.setRequestedAt(dto.getRequestedAt() != null ? dto.getRequestedAt() : Instant.now());
        order.setStatus(dto.getStatus() != null ? dto.getStatus() : OrderStatus.PENDING);
        order.setNotes(dto.getNotes());
        return toDTO(labOrderRepository.save(order));
    }

    /**
     * Entrega el siguiente folio del laboratorio de forma atómica. El contador se
     * lee con bloqueo pesimista y se incrementa dentro de la transacción; si la
     * creación de la orden falla, el incremento se revierte y el folio no se pierde.
     */
    private Long nextOrderNumber(Long laboratoryId) {
        LabOrderCounter counter = labOrderCounterRepository.findById(laboratoryId)
                .orElseGet(() -> {
                    LabOrderCounter created = new LabOrderCounter();
                    created.setLaboratoryId(laboratoryId);
                    created.setNextNumber(1L);
                    return created;
                });
        Long number = counter.getNextNumber();
        counter.setNextNumber(number + 1);
        labOrderCounterRepository.save(counter);
        return number;
    }

    private Long requireLaboratoryId() {
        Long laboratoryId = TenantContext.getLaboratoryId();
        if (laboratoryId == null) {
            throw new APIException("No hay un laboratorio asociado a la sesión actual");
        }
        return laboratoryId;
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
        // Borrado lógico: se marca como cancelada en vez de eliminarla, para que
        // su folio quede consumido y el correlativo no se reutilice ni deje huecos.
        order.setStatus(OrderStatus.CANCELLED);
        labOrderRepository.save(order);
        return toDTO(order);
    }

    private LabOrderDTO toDTO(LabOrder order) {
        LabOrderDTO dto = new LabOrderDTO();
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setCustomerId(order.getCustomer().getId());
        dto.setRequestedAt(order.getRequestedAt());
        dto.setStatus(order.getStatus());
        dto.setNotes(order.getNotes());
        return dto;
    }
}
