package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.LabOrderCounter;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.Test;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.LabOrderResponse;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderCounterRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LabOrderServiceImp implements LabOrderService {

    @Autowired
    private LabOrderRepository labOrderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private LabOrderCounterRepository labOrderCounterRepository;

    @Autowired
    private TestRepository testRepository;

    @Override
    public LabOrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        // El laboratorio (tenant) lo filtra Hibernate por @TenantId; aquí solo se
        // excluyen las canceladas (borrado lógico).
        Page<LabOrder> page = labOrderRepository.findByStatusNotFetchCustomer(OrderStatus.CANCELLED, pageable);
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
        Customer customer = customerRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "customerId", dto.getCustomerId()));
        LabOrder order = new LabOrder();
        // El laboratorio (tenant) lo asigna Hibernate al persistir por @TenantId.
        order.setOrderNumber(nextOrderNumber(laboratoryId));
        // Token del enlace público de resultados: opaco, único e irrepetible, se fija
        // una sola vez aquí y acompaña a la orden toda su vida.
        order.setPublicToken(UUID.randomUUID().toString());
        order.setCustomer(customer);
        order.setRequestedAt(dto.getRequestedAt() != null ? dto.getRequestedAt() : Instant.now());
        order.setStatus(dto.getStatus() != null ? dto.getStatus() : OrderStatus.PENDING);
        order.setNotes(dto.getNotes());
        applyClinicalContext(order, dto);
        // Exámenes de la orden en la misma llamada (opcional). Antes el front creaba
        // la orden y luego hacía un POST /orders/{id}/tests por examen (N requests
        // seriales, cada uno con el piso de ~0.7 s y una invocación de Cloudflare).
        // Aquí se crean los LabTest dentro de la misma transacción y con cascade, así
        // toda la creación es UN solo request. Mismos datos y mismo orden que el alta
        // por examen (sin perfil/notas/muestra); el detalle se recarga aparte.
        attachTests(order, dto.getTestIds());
        return toDTO(labOrderRepository.save(order));
    }

    /**
     * Adjunta los exámenes indicados a una orden recién creada, en el mismo orden
     * recibido, replicando exactamente el alta por examen (sin perfil, notas ni tipo
     * de muestra). Se persisten por cascade al guardar la orden. Si algún examen no
     * existe se lanza la misma excepción que el alta individual y toda la transacción
     * se revierte (creación atómica).
     */
    private void attachTests(LabOrder order, List<Long> testIds) {
        if (testIds == null || testIds.isEmpty()) {
            return;
        }
        List<LabTest> labTests = new ArrayList<>(testIds.size());
        for (Long testId : testIds) {
            Test test = testRepository.findById(testId)
                    .orElseThrow(() -> new ResourceNotFoundException("Test", "testId", testId));
            LabTest labTest = new LabTest();
            labTest.setOrder(order);
            labTest.setTest(test);
            labTest.setTestConfig(null);
            labTests.add(labTest);
        }
        order.setTests(labTests);
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
        applyClinicalContext(order, dto);
        return toDTO(labOrderRepository.save(order));
    }

    @Override
    public LabOrderDTO getOrderById(Long id) {
        return toDTO(labOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LabOrder", "orderId", id)));
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

    // El contexto clínico se guarda tal cual llega del formulario (la orden envía
    // el estado completo). Si no es gestante, se descarta la semana de gestación
    // para no dejar datos incoherentes.
    private void applyClinicalContext(LabOrder order, LabOrderDTO dto) {
        order.setLmpDate(dto.getLmpDate());
        order.setPregnant(dto.isPregnant());
        order.setGestationalWeeks(dto.isPregnant() ? dto.getGestationalWeeks() : null);
        order.setMenopausal(dto.isMenopausal());
    }

    private LabOrderDTO toDTO(LabOrder order) {
        LabOrderDTO dto = new LabOrderDTO();
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setPublicToken(order.getPublicToken());
        dto.setCustomerId(order.getCustomer().getId());
        dto.setCustomerName(order.getCustomer().getName());
        // Sexo/edad del mismo Customer ya cargado (sin query extra): dejan al front
        // resolver los rangos aplicables sin la llamada serial a GET /customers/{id}.
        dto.setCustomerSex(order.getCustomer().getSex());
        dto.setCustomerAgeInDays(order.getCustomer().getAgeInDays());
        dto.setRequestedAt(order.getRequestedAt());
        dto.setStatus(order.getStatus());
        dto.setNotes(order.getNotes());
        dto.setLmpDate(order.getLmpDate());
        dto.setPregnant(order.isPregnant());
        dto.setGestationalWeeks(order.getGestationalWeeks());
        dto.setMenopausal(order.isMenopausal());
        return dto;
    }
}
