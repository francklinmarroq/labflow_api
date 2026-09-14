package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.Invoice;
import marroquinsoftware.labflowapi.model.InvoiceStatus;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.LabOrderCounter;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.OrderTag;
import marroquinsoftware.labflowapi.model.Permission;
import marroquinsoftware.labflowapi.model.Test;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.LabOrderResponse;
import marroquinsoftware.labflowapi.payload.OrderTagDTO;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.InvoiceRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderCounterRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderSpecifications;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private OrderTagService orderTagService;

    @Override
    @Transactional(readOnly = true)
    public LabOrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                         OrderStatus status, Long tagId) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        // El laboratorio (tenant) lo filtra Hibernate por @TenantId. Sin filtro de
        // estado se listan las órdenes activas (excluye canceladas, borrado lógico);
        // con un estado concreto se listan solo esas (p. ej. la pestaña de canceladas).
        Page<LabOrder> page = labOrderRepository.findAll(
                LabOrderSpecifications.orders(status, tagId), pageable);
        // Una sola consulta de facturas para toda la página, antes de mapear.
        Set<Long> locked = lockedOrderIds(page.getContent().stream().map(LabOrder::getId).toList());
        List<LabOrderDTO> dtos = page.getContent().stream().map(o -> toDTO(o, locked)).toList();
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
        order.setReferringPhysician(trimToNull(dto.getReferringPhysician()));
        applyClinicalContext(order, dto);
        applyTags(order, dto.getTagNames());
        // Exámenes de la orden en la misma llamada (opcional). Antes el front creaba
        // la orden y luego hacía un POST /orders/{id}/tests por examen (N requests
        // seriales, cada uno con el piso de ~0.7 s y una invocación de Cloudflare).
        // Aquí se crean los LabTest dentro de la misma transacción y con cascade, así
        // toda la creación es UN solo request. Mismos datos y mismo orden que el alta
        // por examen (sin perfil/notas/muestra); el detalle se recarga aparte.
        attachTests(order, dto.getTestIds());
        // Sin consulta: una orden que acaba de nacer no puede tener factura todavía.
        return toDTO(labOrderRepository.save(order), Set.of());
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
    @Transactional
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
        order.setReferringPhysician(trimToNull(dto.getReferringPhysician()));
        applyClinicalContext(order, dto);
        // Solo se tocan las etiquetas si el cliente las mandó. Varias pantallas
        // actualizan la orden para otra cosa (cambiar de estado al ingresar
        // resultados, por ejemplo) y no envían tagNames; ahí las etiquetas se dejan
        // como estaban. Una lista vacía sí es una instrucción: quitarlas todas.
        if (dto.getTagNames() != null) {
            applyTags(order, dto.getTagNames());
        }
        LabOrder saved = labOrderRepository.save(order);
        return toDTO(saved, lockedOrderIds(List.of(saved.getId())));
    }

    @Override
    public LabOrderDTO getOrderById(Long id) {
        LabOrder order = labOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LabOrder", "orderId", id));
        return toDTO(order, lockedOrderIds(List.of(order.getId())));
    }

    @Override
    @Transactional
    public LabOrderDTO cancelOrder(Long id, String reason) {
        LabOrder order = labOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LabOrder", "orderId", id));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new APIException("La orden ya está cancelada.");
        }

        // Si la orden tiene una factura viva, cancelar la orden implica anularla en
        // cascada: la anulación revierte los pagos y la emisión con contra-asientos
        // (reutiliza InvoiceService.annulInvoice). Como toca la contabilidad, exige
        // además el permiso de anular facturas; una orden sin factura no lo requiere.
        invoiceRepository.findFirstByOrderIdAndStatusNotOrderByIssuedAtDesc(id, InvoiceStatus.ANULADA)
                .ifPresent(invoice -> {
                    if (!hasAuthority(Permission.INVOICES_ANNUL)) {
                        throw new APIException("No tiene permiso para anular la factura de esta orden. "
                                + "Anule primero la factura o solicite el permiso correspondiente.");
                    }
                    invoiceService.annulInvoice(invoice.getId(), reason);
                });

        // Borrado lógico: se marca como cancelada en vez de eliminarla, para que
        // su folio quede consumido y el correlativo no se reutilice ni deje huecos.
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancelledByUsername(currentUsername());
        order.setCancellationReason(reason != null ? reason.trim() : null);
        labOrderRepository.save(order);
        // Se relee después de la posible anulación en cascada: si la factura quedó
        // anulada, la orden ya no está bloqueada y el DTO debe decirlo.
        return toDTO(order, lockedOrderIds(List.of(order.getId())));
    }

    // Normaliza el texto opcional del médico solicitante: recorta espacios y trata
    // el vacío como null, para que el reporte no imprima el rótulo con un valor en
    // blanco (solo aparece si de verdad se llenó).
    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private boolean hasAuthority(Permission permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> permission.name().equals(a.getAuthority()));
    }

    /**
     * Deja la orden con exactamente las etiquetas indicadas por nombre. Las que aún
     * no existan en el laboratorio se dan de alta en el momento (ver
     * {@link OrderTagService#resolveOrCreate}): el usuario escribe "IHSS" en la
     * primera orden del convenio y de ahí en adelante la reutiliza.
     *
     * <p>Se muta la colección existente en vez de reemplazarla para que Hibernate
     * calcule el delta de la tabla de unión sobre la instancia que ya administra.
     */
    private void applyTags(LabOrder order, List<String> tagNames) {
        Set<OrderTag> resolved = orderTagService.resolveOrCreate(tagNames);
        if (order.getTags() == null) {
            order.setTags(new LinkedHashSet<>(resolved));
            return;
        }
        order.getTags().clear();
        order.getTags().addAll(resolved);
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

    /**
     * De las órdenes indicadas, cuáles tienen los exámenes bloqueados por una
     * factura viva. Una sola consulta para todo el conjunto; con la lista vacía no
     * se consulta nada (un `in ()` no tiene nada que responder).
     */
    private Set<Long> lockedOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(invoiceRepository.findOrderIdsWithLiveInvoice(orderIds));
    }

    /**
     * El conjunto de ids bloqueados llega resuelto de afuera, no se consulta acá:
     * toDTO corre una vez por fila del listado y preguntar por la factura de cada
     * orden aquí adentro convertiría una página en una consulta por orden.
     */
    private LabOrderDTO toDTO(LabOrder order, Set<Long> lockedOrderIds) {
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
        // Identidad del mismo Customer ya cargado (sin query extra): deja al front
        // mostrar la identidad en el reporte/sobre sin la llamada serial a
        // GET /customers/{id}.
        dto.setCustomerNationalId(order.getCustomer().getNationalIdNumber());
        dto.setRequestedAt(order.getRequestedAt());
        dto.setStatus(order.getStatus());
        dto.setNotes(order.getNotes());
        dto.setReferringPhysician(order.getReferringPhysician());
        dto.setLmpDate(order.getLmpDate());
        dto.setPregnant(order.isPregnant());
        dto.setGestationalWeeks(order.getGestationalWeeks());
        dto.setMenopausal(order.isMenopausal());
        dto.setCancelledAt(order.getCancelledAt());
        dto.setCancelledByUsername(order.getCancelledByUsername());
        dto.setCancellationReason(order.getCancellationReason());
        // Solo lectura: nunca se lee del DTO entrante, se deriva de la factura viva.
        dto.setTestsLocked(lockedOrderIds.contains(order.getId()));
        // Etiquetas con id, nombre y color, listas para pintarse. Se resuelven por
        // lotes gracias al @BatchSize de LabOrder.tags, así que un listado de 500
        // órdenes no dispara 500 consultas. Sin conteo de uso: eso solo interesa en
        // la pantalla del catálogo de etiquetas.
        if (order.getTags() != null && !order.getTags().isEmpty()) {
            dto.setTags(order.getTags().stream()
                    .map(t -> new OrderTagDTO(t.getId(), t.getName(), t.getColor(), null))
                    .toList());
        } else {
            dto.setTags(List.of());
        }
        return dto;
    }
}
