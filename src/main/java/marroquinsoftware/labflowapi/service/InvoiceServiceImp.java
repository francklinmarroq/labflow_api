package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.*;
import marroquinsoftware.labflowapi.repositories.*;
import marroquinsoftware.labflowapi.service.JournalService.LinePlan;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InvoiceServiceImp implements InvoiceService {

    // Los instantes (issuedAt) se guardan en UTC, pero el negocio opera en
    // Honduras (UTC-6, sin horario de verano). "Hoy" y los rangos de fecha se
    // resuelven en esta zona; con UTC, una factura emitida de tarde caía en el
    // día siguiente y se perdía en los filtros por fecha.
    private static final ZoneId LAB_ZONE = ZoneId.of("America/Tegucigalpa");

    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentCounterRepository paymentCounterRepository;
    @Autowired private LabOrderRepository labOrderRepository;
    @Autowired private LaboratoryRepository laboratoryRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BillingClientRepository billingClientRepository;
    @Autowired private CaiNumberService caiNumberService;
    @Autowired private AgeDiscountCalculator ageDiscountCalculator;
    @Autowired private InvoiceTotalsCalculator invoiceTotalsCalculator;
    @Autowired private AmountInWordsConverter amountInWordsConverter;
    @Autowired private JournalService journalService;
    @Autowired private UserRepository userRepository;
    @Autowired private InvoiceOrderRepository invoiceOrderRepository;
    @Autowired private TestRepository testRepository;

    /**
     * Nombre para mostrar de quien hizo una acción (emitir/anular): el nombre de la
     * persona, o el correo si no tiene nombre (usuarios antiguos) o ya no existe.
     */
    private String displayName(String username) {
        if (username == null) return null;
        return userRepository.findNameByUsernameAndLaboratoryId(username, TenantContext.getLaboratoryId())
                .filter(n -> !n.isBlank())
                .orElse(username);
    }

    @Override
    public InvoicePreviewDTO previewInvoice(Long orderId) {
        // La vista previa de UNA orden (la que lee la pantalla de la orden): mismo
        // armado que la emisión, sin rechazar una orden ya facturada, porque aquí
        // justamente se quiere saber cuál es su factura.
        Laboratory laboratory = currentLaboratory();
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("LabOrder", "orderId", orderId));
        DraftInput input = new DraftInput(List.of(orderId), List.of(), List.of(), null, List.of(),
                null, null, null);
        Draft draft = buildDraft(input, laboratory, false);
        Customer customer = order.getCustomer();
        Invoice existing = invoiceOrderRepository.findLiveInvoiceOfOrder(orderId).orElse(null);
        OrderGroup group = draft.orders().get(0);
        return new InvoicePreviewDTO(
                order.getId(),
                order.getOrderNumber(),
                customer.getId(),
                customer.getName(),
                customer.getTaxNumber(),
                group.kind(),
                group.kind().getLabel(),
                group.percent(),
                draft.subtotal(),
                group.ruleDiscount(),
                draft.subtotal().subtract(group.ruleDiscount()),
                draft.itemDTOs(),
                existing != null ? existing.getId() : null,
                existing != null ? existing.getInvoiceNumber() : null);
    }

    @Override
    public InvoiceDraftPreviewDTO previewDraft(InvoiceDraftRequest request) {
        Laboratory laboratory = currentLaboratory();
        DraftInput input = new DraftInput(request.getOrderIds(), request.getTests(), request.getConcepts(),
                request.getRecipient(), request.getItemPrices(), null, null, request.getTotal());
        Draft draft = buildDraft(input, laboratory, true);
        return new InvoiceDraftPreviewDTO(
                draft.itemDTOs(),
                draft.orders().stream().map(g -> new InvoiceOrderDTO(g.order().getId(), g.order().getOrderNumber(),
                        g.customer().getId(), g.customer().getName(), g.kind(), g.kind().getLabel(), g.percent(),
                        g.charged(), g.ruleDiscount())).toList(),
                draft.recipient().name(),
                draft.recipient().rtn(),
                draft.patientName(),
                draft.totals().subtotal(),
                draft.totals().itemDiscount(),
                draft.discountKind(),
                discountLabel(draft.discountKind(), draft.totals().ageDiscount()),
                draft.discountPercent(),
                draft.totals().ageDiscount(),
                draft.totals().otherDiscount(),
                draft.totals().total());
    }

    @Override
    @Transactional
    public InvoiceDTO createInvoice(InvoiceRequest request) {
        Long laboratoryId = requireLaboratoryId();

        // El lock sobre el laboratorio serializa por tenant la numeración CAI y,
        // de paso, el chequeo de doble facturación de las órdenes: dos emisiones
        // simultáneas no pueden tomar la misma orden.
        Laboratory laboratory = laboratoryRepository.findWithLockById(laboratoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", "id", laboratoryId));

        List<Long> orderIds = new ArrayList<>();
        if (request.getOrderIds() != null) orderIds.addAll(request.getOrderIds());
        if (request.getOrderId() != null && !orderIds.contains(request.getOrderId())) {
            orderIds.add(0, request.getOrderId());
        }
        DraftInput input = new DraftInput(orderIds, request.getTests(), request.getConcepts(),
                request.getRecipient(), request.getItemPrices(), request.getBillingClientId(),
                request.getCustomerRtn(), request.getTotal());

        // Todo lo que puede fallar (órdenes ya facturadas, precios, conceptos,
        // destinatario, totales) se resuelve aquí, ANTES de tocar el correlativo
        // CAI: un número fiscal gastado es irrecuperable (deja un hueco en el
        // rango autorizado por el SAR).
        Draft draft = buildDraft(input, laboratory, true);
        Totals totals = draft.totals();
        BigDecimal total = totals.total();

        Invoice invoice = new Invoice();
        List<InvoiceItem> items = new ArrayList<>();
        for (DraftLine line : draft.lines()) {
            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setItemType(line.type());
            item.setTestId(line.testId());
            item.setTestName(line.name());
            item.setQuantity(line.quantity());
            item.setListPrice(line.listUnit());
            item.setPrice(line.chargedUnit());
            items.add(item);
        }
        invoice.setItems(items);

        List<InvoiceOrder> invoiceOrders = new ArrayList<>();
        for (OrderGroup group : draft.orders()) {
            InvoiceOrder io = new InvoiceOrder();
            io.setInvoice(invoice);
            io.setOrder(group.order());
            io.setCustomer(group.customer());
            io.setPatientName(group.customer().getName());
            io.setAgeDiscountKind(group.kind());
            io.setAgePercent(group.percent());
            io.setChargedAmount(group.charged());
            io.setAgeDiscountAmount(group.ruleDiscount());
            invoiceOrders.add(io);
        }
        invoice.setInvoiceOrders(invoiceOrders);

        Recipient recipient = draft.recipient();
        invoice.setCustomer(draft.customer());
        invoice.setBillingClient(recipient.billingClient());
        invoice.setCustomerName(recipient.name());
        invoice.setCustomerRtn(recipient.rtn());
        // Paciente de la factura cuando sus órdenes son de uno solo; con varios
        // pacientes queda null y la factura los lista por orden.
        invoice.setPatientName(draft.patientName());

        invoice.setDiscountKind(draft.discountKind());
        invoice.setDiscountPercent(draft.discountPercent());
        invoice.setSubtotal(totals.subtotal());
        invoice.setDiscountAmount(totals.ageDiscount());
        invoice.setOtherDiscountAmount(totals.otherDiscount());
        invoice.setTotal(total);
        invoice.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        // Fecha de emisión: hoy por defecto, o la que indique el operador para
        // antedatar. Se valida antes del CAI por la misma razón que lo demás.
        LocalDate today = LocalDate.now(LAB_ZONE);
        LocalDate issueDate = request.getIssueDate() != null ? request.getIssueDate() : today;
        if (issueDate.isAfter(today)) {
            throw new APIException("La fecha de la factura no puede ser futura.");
        }

        // Número fiscal y snapshot del CAI y del emisor con los que se imprimió.
        CaiNumberService.IssuedCaiNumber issued = caiNumberService.next(laboratory);
        laboratoryRepository.save(laboratory);
        invoice.setInvoiceNumber(issued.invoiceNumber());
        invoice.setCai(issued.cai());
        invoice.setCaiRangeFrom(issued.rangeFrom());
        invoice.setCaiRangeTo(issued.rangeTo());
        invoice.setCaiExpirationDate(issued.expirationDate());
        invoice.setLabName(laboratory.getName());
        invoice.setLabTaxName(laboratory.getTaxName());
        invoice.setLabRtn(laboratory.getRtn());
        invoice.setLabAddress(joinAddress(laboratory));
        invoice.setLabTaxAddress(laboratory.getTaxAddress());
        invoice.setLabPhone(laboratory.getPhone());
        invoice.setLabEmail(laboratory.getEmail());
        invoice.setLabHeadline(laboratory.getInvoiceHeadline());
        invoice.setLabFooterNote(laboratory.getInvoiceFooterNote());
        invoice.setLabPacNumber(laboratory.getPacNumber());
        invoice.setLabRegExonerado(laboratory.getRegExonerado());
        invoice.setLabRegSag(laboratory.getRegSag());
        invoice.setLabOrdenCompraExenta(laboratory.getOrdenCompraExenta());

        // La hora se conserva realista para las de hoy; para una fecha pasada se
        // fija a mediodía, así ninguna zona horaria la corre de día al imprimir. El
        // asiento y el pago inicial usan esta misma fecha.
        Instant issuedAt = issueDate.isEqual(today)
                ? Instant.now()
                : issueDate.atTime(LocalTime.NOON).atZone(LAB_ZONE).toInstant();

        invoice.setIssuedAt(issuedAt);
        invoice.setIssuedByUsername(currentUsername());
        invoice.setSaleCondition(request.getSaleCondition());
        invoice.setStatus(total.compareTo(BigDecimal.ZERO) == 0 ? InvoiceStatus.PAGADA : InvoiceStatus.PENDIENTE);
        invoice = invoiceRepository.save(invoice);

        postIssueEntry(invoice, issueDate);

        // Contado: el pago completo entra en la misma transacción. Crédito: el
        // abono inicial es opcional. El pago inicial hereda la fecha de la factura.
        PaymentRequest initialPayment = request.getInitialPayment();
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            if (request.getSaleCondition() == SaleCondition.CONTADO) {
                if (initialPayment == null
                        || initialPayment.getAmount().setScale(2, RoundingMode.HALF_UP).compareTo(total) != 0) {
                    throw new APIException("La venta al contado exige el pago completo (L " + total + ").");
                }
                applyPayment(invoice, initialPayment, issueDate, issuedAt);
            } else if (initialPayment != null) {
                applyPayment(invoice, initialPayment, issueDate, issuedAt);
            }
        }

        return toDTO(invoice, true);
    }

    @Override
    @Transactional(readOnly = true)
    public UninvoicedOrderResponse getUninvoicedOrders(Integer pageNumber, Integer pageSize, Long customerId,
                                                       LocalDate from, LocalDate to) {
        // Rango [from 00:00, to+1 00:00) en hora de Honduras, como el listado de facturas.
        Instant fromInstant = from != null ? from.atStartOfDay(LAB_ZONE).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(LAB_ZONE).toInstant() : null;
        Page<LabOrder> page = labOrderRepository.findAll(
                LabOrderSpecifications.uninvoiced(customerId, fromInstant, toInstant),
                PageRequest.of(pageNumber, pageSize, Sort.by("requestedAt").descending()));
        UninvoicedOrderResponse response = new UninvoicedOrderResponse();
        response.setContent(page.getContent().stream().map(order -> {
            List<LabTest> tests = order.getTests() == null ? List.of() : order.getTests();
            BigDecimal catalogTotal = tests.stream()
                    .map(lt -> lt.getTest() != null && lt.getTest().getPrice() != null
                            ? lt.getTest().getPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            return new UninvoicedOrderDTO(order.getId(), order.getOrderNumber(), order.getRequestedAt(),
                    order.getCustomer().getId(), order.getCustomer().getName(), tests.size(), catalogTotal);
        }).toList());
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    // --- Armado del borrador ------------------------------------------------
    //
    // Vista previa y emisión arman la factura con el MISMO código, para que lo que
    // se ve antes de emitir sea exactamente lo que se guarda.

    /** Lo que pide quien arma la factura, en las formas nueva y vieja. */
    private record DraftInput(List<Long> orderIds, List<InvoiceTestLineRequest> tests,
                              List<InvoiceConceptRequest> concepts, InvoiceRecipientDTO recipient,
                              List<InvoiceItemPriceDTO> itemPrices, Long legacyBillingClientId,
                              String legacyCustomerRtn, BigDecimal requestedTotal) {
    }

    /** Una línea de la factura ya agrupada; precios unitarios. */
    private record DraftLine(InvoiceItemType type, Long testId, String name, BigDecimal quantity,
                             BigDecimal listUnit, BigDecimal chargedUnit) {
        BigDecimal listAmount() { return listUnit.multiply(quantity).setScale(2, RoundingMode.HALF_UP); }
        BigDecimal amount() { return chargedUnit.multiply(quantity).setScale(2, RoundingMode.HALF_UP); }
    }

    /** Una orden de la factura con lo que se cobra por sus exámenes y su descuento por edad. */
    private record OrderGroup(LabOrder order, Customer customer, AgeDiscountKind kind, BigDecimal percent,
                              BigDecimal charged, BigDecimal ruleDiscount) {
    }

    /** A nombre de quién sale la factura, ya resuelto. */
    private record Recipient(String name, String rtn, BillingClient billingClient, Customer patient) {
    }

    private record Draft(List<DraftLine> lines, List<OrderGroup> orders, Recipient recipient,
                         Customer customer, String patientName, BigDecimal subtotal, Totals totals,
                         AgeDiscountKind discountKind, BigDecimal discountPercent) {
        List<InvoiceItemDTO> itemDTOs() {
            return lines.stream().map(l -> new InvoiceItemDTO(null, l.testId(), null, l.name(), l.listUnit(),
                    l.chargedUnit(), l.quantity(), l.type(), l.amount())).toList();
        }
    }

    private record Totals(BigDecimal subtotal, BigDecimal itemDiscount, BigDecimal ageDiscount,
                          BigDecimal otherDiscount, BigDecimal total) {
    }

    /**
     * Arma la factura: órdenes, líneas agrupadas por examen, conceptos, descuento
     * por edad por orden, destinatario y totales.
     *
     * @param validateOrders rechazar órdenes canceladas, vacías o ya facturadas (la
     *                       emisión y la vista previa del borrador; la vista previa
     *                       de una orden no, porque ahí se pregunta su factura)
     */
    private Draft buildDraft(DraftInput input, Laboratory laboratory, boolean validateOrders) {
        // 1. Órdenes, sin repetir y en el orden en que se eligieron.
        List<LabOrder> orders = new ArrayList<>();
        for (Long orderId : input.orderIds() == null ? List.<Long>of() : input.orderIds()) {
            if (orderId == null || orders.stream().anyMatch(o -> o.getId().equals(orderId))) continue;
            LabOrder order = labOrderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("LabOrder", "orderId", orderId));
            if (validateOrders) requireInvoiceable(order);
            orders.add(order);
        }

        // 2. Precios especiales por examen del catálogo. La forma vieja los manda
        //    por examen de la orden (labTestId): se traducen a su examen.
        Map<Long, BigDecimal> specialPrices = new HashMap<>();
        for (InvoiceItemPriceDTO adjustment : input.itemPrices() == null
                ? List.<InvoiceItemPriceDTO>of() : input.itemPrices()) {
            Long testId = adjustment.getTestId();
            if (testId == null && adjustment.getLabTestId() != null) {
                testId = orders.stream()
                        .flatMap(o -> (o.getTests() == null ? List.<LabTest>of() : o.getTests()).stream())
                        .filter(lt -> lt.getId().equals(adjustment.getLabTestId()) && lt.getTest() != null)
                        .map(lt -> lt.getTest().getId())
                        .findFirst()
                        .orElseThrow(() -> new APIException(
                                "Hay un precio especial para un examen que no está en la factura."));
            }
            if (testId == null || adjustment.getPrice() == null) {
                throw new APIException("Cada precio especial debe indicar el examen y el precio.");
            }
            specialPrices.put(testId, adjustment.getPrice().setScale(2, RoundingMode.HALF_UP));
        }

        // 3. Unidades por examen: las de cada orden y las de los exámenes sueltos.
        //    Todas las unidades del mismo examen van a UNA línea con su cantidad.
        Map<Long, Test> testsById = new HashMap<>();
        Map<Long, BigDecimal> unitsByTest = new java.util.LinkedHashMap<>();
        Map<Long, Map<Long, BigDecimal>> unitsByOrder = new HashMap<>(); // orderId -> testId -> unidades
        for (LabOrder order : orders) {
            Map<Long, BigDecimal> units = new java.util.LinkedHashMap<>();
            for (LabTest labTest : order.getTests() == null ? List.<LabTest>of() : order.getTests()) {
                Test test = labTest.getTest();
                if (test == null) continue;
                testsById.put(test.getId(), test);
                units.merge(test.getId(), BigDecimal.ONE, BigDecimal::add);
                unitsByTest.merge(test.getId(), BigDecimal.ONE, BigDecimal::add);
            }
            unitsByOrder.put(order.getId(), units);
        }
        Map<Long, BigDecimal> looseUnits = new java.util.LinkedHashMap<>();
        for (InvoiceTestLineRequest line : input.tests() == null ? List.<InvoiceTestLineRequest>of() : input.tests()) {
            if (line.getTestId() == null || line.getQuantity() == null || line.getQuantity() < 1) {
                throw new APIException("Cada examen agregado necesita el examen y una cantidad de al menos 1.");
            }
            Test test = testsById.computeIfAbsent(line.getTestId(), id -> testRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Test", "testId", id)));
            BigDecimal qty = BigDecimal.valueOf(line.getQuantity());
            unitsByTest.merge(test.getId(), qty, BigDecimal::add);
            looseUnits.merge(test.getId(), qty, BigDecimal::add);
        }
        for (Long testId : specialPrices.keySet()) {
            if (!unitsByTest.containsKey(testId)) {
                throw new APIException("Hay un precio especial para un examen que no está en la factura.");
            }
        }

        // 4. Líneas de examen, con el precio especial del examen o el de catálogo.
        List<DraftLine> lines = new ArrayList<>();
        Map<Long, BigDecimal> chargedUnitByTest = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : unitsByTest.entrySet()) {
            Test test = testsById.get(entry.getKey());
            BigDecimal listUnit = test.getPrice() != null
                    ? test.getPrice().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
            BigDecimal chargedUnit = specialPrices.getOrDefault(test.getId(), listUnit);
            if (chargedUnit.compareTo(listUnit) > 0) {
                throw new APIException("El precio de «" + test.getName() + "» (L " + chargedUnit
                        + ") no puede superar el de catálogo (L " + listUnit + ").");
            }
            chargedUnitByTest.put(test.getId(), chargedUnit);
            lines.add(new DraftLine(InvoiceItemType.EXAMEN, test.getId(), test.getName(),
                    entry.getValue(), listUnit, chargedUnit));
        }

        // 5. Conceptos libres: cada uno su línea, sin agruparse entre sí.
        BigDecimal conceptsCharged = BigDecimal.ZERO;
        int position = 0;
        for (InvoiceConceptRequest concept : input.concepts() == null
                ? List.<InvoiceConceptRequest>of() : input.concepts()) {
            position++;
            String description = concept.getDescription() != null ? concept.getDescription().trim() : "";
            if (description.isEmpty()) {
                throw new APIException("El concepto " + position + " necesita una descripción.");
            }
            if (concept.getQuantity() == null || concept.getQuantity().signum() <= 0
                    || concept.getUnitPrice() == null || concept.getUnitPrice().signum() <= 0) {
                throw new APIException("El concepto «" + description
                        + "» necesita cantidad y precio mayores que cero.");
            }
            BigDecimal unit = concept.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
            DraftLine line = new DraftLine(InvoiceItemType.CONCEPTO, null, description,
                    concept.getQuantity().setScale(3, RoundingMode.HALF_UP), unit, unit);
            lines.add(line);
            conceptsCharged = conceptsCharged.add(line.amount());
        }
        if (lines.isEmpty()) {
            throw new APIException("La factura necesita al menos una línea: una orden, un examen o un concepto.");
        }

        // 6. Descuento por edad por orden, según su propio paciente. Los exámenes
        //    sueltos y los conceptos forman un grupo sin descuento.
        List<OrderGroup> groups = new ArrayList<>();
        List<InvoiceTotalsCalculator.AgeGroup> ageGroups = new ArrayList<>();
        for (LabOrder order : orders) {
            BigDecimal charged = BigDecimal.ZERO;
            for (Map.Entry<Long, BigDecimal> units : unitsByOrder.get(order.getId()).entrySet()) {
                charged = charged.add(chargedUnitByTest.get(units.getKey()).multiply(units.getValue()));
            }
            charged = charged.setScale(2, RoundingMode.HALF_UP);
            Customer patient = order.getCustomer();
            AgeDiscountDTO discount = ageDiscountCalculator.discountFor(patient.getAgeInDays(), laboratory);
            BigDecimal percent = discount.getPercent() != null ? discount.getPercent() : BigDecimal.ZERO;
            InvoiceTotalsCalculator.AgeGroup ageGroup = new InvoiceTotalsCalculator.AgeGroup(charged, percent);
            ageGroups.add(ageGroup);
            groups.add(new OrderGroup(order, patient, discount.getKind(), percent, charged, ageGroup.ruleDiscount()));
        }
        BigDecimal looseCharged = conceptsCharged;
        for (Map.Entry<Long, BigDecimal> units : looseUnits.entrySet()) {
            looseCharged = looseCharged.add(chargedUnitByTest.get(units.getKey()).multiply(units.getValue()));
        }
        if (looseCharged.signum() > 0) {
            ageGroups.add(new InvoiceTotalsCalculator.AgeGroup(looseCharged.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO));
        }

        BigDecimal subtotal = lines.stream().map(DraftLine::listAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        InvoiceTotalsCalculator.Totals computed = invoiceTotalsCalculator.compute(subtotal, ageGroups,
                input.requestedTotal());
        Totals totals = new Totals(computed.subtotal(), computed.itemDiscount(), computed.ageDiscount(),
                computed.otherDiscount(), computed.total());

        // 7. Tramo de edad de la factura: el de sus órdenes con descuento si todas
        //    comparten tramo y porcentaje; null si se mezclan.
        List<OrderGroup> discounted = groups.stream()
                .filter(g -> g.percent().signum() > 0 && g.kind() != AgeDiscountKind.NONE).toList();
        AgeDiscountKind kind;
        BigDecimal percent;
        if (discounted.isEmpty()) {
            kind = AgeDiscountKind.NONE;
            percent = BigDecimal.ZERO.setScale(2);
        } else if (discounted.stream().allMatch(g -> g.kind() == discounted.get(0).kind()
                && g.percent().compareTo(discounted.get(0).percent()) == 0)) {
            kind = discounted.get(0).kind();
            // Porcentaje realmente aplicado: el descuento por edad es un techo; si en
            // mostrador se rebaja menos que la regla, el % se recorta igual que el
            // monto, para no imprimir "4ta edad 20%" junto a un monto que es un 10%.
            BigDecimal base = discounted.stream().map(OrderGroup::charged).reduce(BigDecimal.ZERO, BigDecimal::add);
            percent = base.signum() > 0
                    ? totals.ageDiscount().multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2);
        } else {
            kind = null;
            percent = null;
        }

        // 8. Destinatario, paciente de la factura y el paciente que la factura apunta.
        java.util.Set<Long> patientIds = new java.util.LinkedHashSet<>();
        orders.forEach(o -> patientIds.add(o.getCustomer().getId()));
        Customer singlePatient = patientIds.size() == 1 ? orders.get(0).getCustomer() : null;
        Recipient recipient = resolveRecipient(input, singlePatient);
        Customer customer = singlePatient != null ? singlePatient : recipient.patient();
        String patientName = singlePatient != null ? singlePatient.getName()
                : (orders.isEmpty() && recipient.patient() != null ? recipient.patient().getName() : null);

        return new Draft(lines, groups, recipient, customer, patientName, subtotal, totals, kind, percent);
    }

    /** Una orden se puede facturar si no está cancelada, tiene exámenes y no tiene factura vigente. */
    private void requireInvoiceable(LabOrder order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new APIException("La orden Nº " + order.getOrderNumber() + " está cancelada y no se puede facturar.");
        }
        if (order.getTests() == null || order.getTests().isEmpty()) {
            throw new APIException("La orden Nº " + order.getOrderNumber() + " no tiene exámenes para facturar.");
        }
        invoiceOrderRepository.findLiveInvoiceOfOrder(order.getId()).ifPresent(existing -> {
            throw new APIException("La orden Nº " + order.getOrderNumber() + " ya está facturada en la factura Nº "
                    + existing.getInvoiceNumber() + ".");
        });
    }

    /**
     * A nombre de quién sale. Sin destinatario explícito vale la forma de siempre:
     * el cliente de facturación si vino, o el paciente si todas las órdenes son de
     * uno solo. Las búsquedas por id solo ven el laboratorio en contexto
     * (@TenantId), así que uno de otro laboratorio es inexistente.
     */
    private Recipient resolveRecipient(DraftInput input, Customer singlePatient) {
        InvoiceRecipientDTO requested = input.recipient();
        if (requested == null || requested.getType() == null) {
            if (input.legacyBillingClientId() != null) {
                requested = new InvoiceRecipientDTO(InvoiceRecipientType.BILLING_CLIENT, null,
                        input.legacyBillingClientId(), null, null);
            } else if (singlePatient != null) {
                requested = new InvoiceRecipientDTO(InvoiceRecipientType.PATIENT, singlePatient.getId(),
                        null, null, input.legacyCustomerRtn());
            } else {
                throw new APIException("Indique a nombre de quién se emite la factura: un paciente, "
                        + "un cliente de facturación o consumidor final.");
            }
        }
        String typedRtn = requested.getRtn() != null && !requested.getRtn().isBlank()
                ? requested.getRtn().trim() : null;
        switch (requested.getType()) {
            case PATIENT -> {
                if (requested.getCustomerId() == null) {
                    throw new APIException("Seleccione el paciente a cuyo nombre se emite la factura.");
                }
                Long customerId = requested.getCustomerId();
                Customer patient = customerRepository.findById(customerId)
                        .orElseThrow(() -> new ResourceNotFoundException("Customer", "customerId", customerId));
                return new Recipient(patient.getName(), typedRtn != null ? typedRtn : patient.getTaxNumber(),
                        null, patient);
            }
            case BILLING_CLIENT -> {
                if (requested.getBillingClientId() == null) {
                    throw new APIException("Seleccione el cliente de facturación.");
                }
                Long clientId = requested.getBillingClientId();
                BillingClient client = billingClientRepository.findById(clientId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "BillingClient", "billingClientId", clientId));
                // A nombre de la empresa: nombre y RTN salen de su ficha; un RTN
                // escrito a mano se ignora a propósito.
                return new Recipient(client.getName(), client.getRtn(), client, null);
            }
            default -> {
                String name = requested.getName() != null ? requested.getName().trim() : "";
                if (name.isEmpty()) {
                    throw new APIException("Escriba el nombre del consumidor final.");
                }
                return new Recipient(name, typedRtn, null, null);
            }
        }
    }

    private Laboratory currentLaboratory() {
        Long laboratoryId = requireLaboratoryId();
        return laboratoryRepository.findById(laboratoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", "id", laboratoryId));
    }

    /** Etiqueta del descuento por edad; con tramos mezclados, solo el concepto. */
    private static String discountLabel(AgeDiscountKind kind, BigDecimal ageDiscount) {
        if (kind != null) return kind.getLabel();
        return ageDiscount != null && ageDiscount.signum() > 0
                ? "Descuento por edad (varios tramos)" : AgeDiscountKind.NONE.getLabel();
    }

    @Override
    public InvoiceResponse getAllInvoices(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                          InvoiceStatus status, Long orderId, LocalDate from, LocalDate to,
                                          String search, Long tagId, Long billingClientId) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        // El rango es [from 00:00, to+1 00:00) en hora de Honduras; el límite
        // superior exclusivo (ver BillingSpecifications) incluye todo el día "to".
        Instant fromInstant = from != null ? from.atStartOfDay(LAB_ZONE).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(LAB_ZONE).toInstant() : null;
        Page<Invoice> page = invoiceRepository.findAll(
                BillingSpecifications.invoices(status, orderId, fromInstant, toInstant, search, tagId,
                        billingClientId), pageable);
        InvoiceResponse response = new InvoiceResponse();
        response.setContent(page.getContent().stream().map(i -> toDTO(i, false)).toList());
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    @Override
    public InvoiceDTO getInvoice(Long invoiceId) {
        return toDTO(findInvoice(invoiceId), true);
    }

    @Override
    @Transactional
    public InvoiceDTO annulInvoice(Long invoiceId, String reason) {
        Invoice invoice = invoiceRepository.findWithLockById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "invoiceId", invoiceId));
        if (invoice.getStatus() == InvoiceStatus.ANULADA) {
            throw new APIException("Esta factura ya está anulada.");
        }

        // Primero se revierte cada pago activo y después la emisión, para que el
        // diario quede espejo exacto de lo que había.
        for (Payment payment : paymentRepository.findByInvoiceIdOrderByPaidAtAsc(invoiceId)) {
            if (payment.isAnnulled()) continue;
            journalService.reverse(
                    journalService.findSourceEntry(JournalSourceType.PAGO, payment.getId()),
                    JournalSourceType.ANULACION_PAGO,
                    payment.getId(),
                    "Anulación de pago (recibo Nº " + payment.getPaymentNumber() + ") por anulación de la factura Nº "
                            + invoice.getInvoiceNumber());
            payment.setAnnulled(true);
            payment.setAnnulledAt(Instant.now());
            payment.setAnnulledByUsername(currentUsername());
            payment.setAnnulmentReason("Anulación de la factura Nº " + invoice.getInvoiceNumber());
            paymentRepository.save(payment);
        }

        // Una factura de cortesía sin valor puede no tener partida de emisión;
        // en ese caso no hay nada que revertir.
        journalService.findSourceEntryIfExists(JournalSourceType.FACTURA, invoice.getId())
                .ifPresent(entry -> journalService.reverse(
                        entry,
                        JournalSourceType.ANULACION_FACTURA,
                        invoice.getId(),
                        "Anulación de factura Nº " + invoice.getInvoiceNumber()));

        invoice.setStatus(InvoiceStatus.ANULADA);
        invoice.setAnnulledAt(Instant.now());
        invoice.setAnnulledByUsername(currentUsername());
        invoice.setAnnulmentReason(reason != null ? reason.trim() : null);
        return toDTO(invoiceRepository.save(invoice), true);
    }

    @Override
    @Transactional
    public InvoiceDTO registerPayment(Long invoiceId, PaymentRequest request) {
        // El lock evita que dos abonos concurrentes cobren de más sobre el mismo saldo.
        Invoice invoice = invoiceRepository.findWithLockById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "invoiceId", invoiceId));
        if (invoice.getStatus() == InvoiceStatus.ANULADA) {
            throw new APIException("No se puede abonar a una factura anulada.");
        }
        if (invoice.getStatus() == InvoiceStatus.PAGADA) {
            throw new APIException("Esta factura ya está pagada.");
        }
        applyPayment(invoice, request);
        return toDTO(invoice, true);
    }

    @Override
    @Transactional
    public InvoiceDTO annulPayment(Long invoiceId, Long paymentId, String reason) {
        Invoice invoice = invoiceRepository.findWithLockById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "invoiceId", invoiceId));
        if (invoice.getStatus() == InvoiceStatus.ANULADA) {
            throw new APIException("Los pagos de una factura anulada ya quedaron anulados con ella.");
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "paymentId", paymentId));
        if (!payment.getInvoice().getId().equals(invoice.getId())) {
            throw new APIException("El pago no pertenece a esta factura.");
        }
        if (payment.isAnnulled()) {
            throw new APIException("Este pago ya está anulado.");
        }

        journalService.reverse(
                journalService.findSourceEntry(JournalSourceType.PAGO, payment.getId()),
                JournalSourceType.ANULACION_PAGO,
                payment.getId(),
                "Anulación de pago (recibo Nº " + payment.getPaymentNumber() + ") de la factura Nº "
                        + invoice.getInvoiceNumber());

        payment.setAnnulled(true);
        payment.setAnnulledAt(Instant.now());
        payment.setAnnulledByUsername(currentUsername());
        payment.setAnnulmentReason(reason != null ? reason.trim() : null);
        paymentRepository.save(payment);

        BigDecimal paid = invoice.getPaidAmount().subtract(payment.getAmount()).setScale(2, RoundingMode.HALF_UP);
        invoice.setPaidAmount(paid.max(BigDecimal.ZERO));
        invoice.setStatus(statusFor(invoice));
        return toDTO(invoiceRepository.save(invoice), true);
    }

    @Override
    public ReceivablesResponse getReceivables(Integer pageNumber, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize,
                Sort.by("issuedAt").ascending()); // las más viejas primero: son las que urge cobrar
        Page<Invoice> page = invoiceRepository.findReceivables(pageable);
        ReceivablesResponse response = new ReceivablesResponse();
        response.setContent(page.getContent().stream().map(i -> toDTO(i, false)).toList());
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        response.setTotalReceivable(invoiceRepository.totalReceivable().setScale(2, RoundingMode.HALF_UP));
        return response;
    }

    @Override
    public CustomerStatementDTO getCustomerStatement(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "customerId", customerId));
        return statementOf(customer.getId(), customer.getName(),
                invoiceRepository.findByCustomerIdOrderByIssuedAtAsc(customerId));
    }

    @Override
    public CustomerStatementDTO getBillingClientStatement(Long billingClientId) {
        BillingClient client = billingClientRepository.findById(billingClientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingClient", "billingClientId", billingClientId));
        return statementOf(client.getId(), client.getName(),
                invoiceRepository.findByBillingClientIdOrderByIssuedAtAsc(billingClientId));
    }

    @Override
    public List<BillingClientBalanceDTO> getReceivablesByBillingClient() {
        return invoiceRepository.receivablesByBillingClient().stream()
                .map(row -> new BillingClientBalanceDTO(
                        (Long) row[0],
                        (String) row[1],
                        (Long) row[2],
                        ((BigDecimal) row[3]).setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    /**
     * Arma el estado de cuenta de un titular —paciente o cliente de facturación—
     * a partir de sus facturas: cargos y abonos activos mezclados en orden
     * cronológico con el saldo corriendo. Lo comparten los dos reportes para que
     * no se pueda arreglar la lógica en uno y dejarla vieja en el otro.
     *
     * <p>Las facturas anuladas no entran, y de las que entran solo cuentan los
     * pagos no anulados: el estado de cuenta refleja lo que de verdad se debe.
     */
    private CustomerStatementDTO statementOf(Long holderId, String holderName, List<Invoice> invoices) {
        record Event(Instant date, String description, BigDecimal charge, BigDecimal payment) {}
        List<Event> events = new ArrayList<>();
        BigDecimal totalInvoiced = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (Invoice invoice : invoices) {
            if (invoice.getStatus() == InvoiceStatus.ANULADA) continue;
            events.add(new Event(invoice.getIssuedAt(),
                    "Factura Nº " + invoice.getInvoiceNumber(), invoice.getTotal(), null));
            totalInvoiced = totalInvoiced.add(invoice.getTotal());
            for (Payment payment : paymentRepository.findByInvoiceIdOrderByPaidAtAsc(invoice.getId())) {
                if (payment.isAnnulled()) continue;
                events.add(new Event(payment.getPaidAt(),
                        "Recibo Nº " + payment.getPaymentNumber() + " — Factura Nº " + invoice.getInvoiceNumber(),
                        null, payment.getAmount()));
                totalPaid = totalPaid.add(payment.getAmount());
            }
        }
        events.sort(Comparator.comparing(Event::date));

        BigDecimal balance = BigDecimal.ZERO;
        List<CustomerStatementRowDTO> rows = new ArrayList<>();
        for (Event event : events) {
            balance = balance
                    .add(event.charge() != null ? event.charge() : BigDecimal.ZERO)
                    .subtract(event.payment() != null ? event.payment() : BigDecimal.ZERO);
            rows.add(new CustomerStatementRowDTO(event.date(), event.description(),
                    event.charge(), event.payment(), balance));
        }

        return new CustomerStatementDTO(holderId, holderName, rows,
                totalInvoiced.setScale(2, RoundingMode.HALF_UP),
                totalPaid.setScale(2, RoundingMode.HALF_UP),
                balance.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Registra el pago con su recibo y su partida, y actualiza saldo y estado.
     * La factura ya viene bloqueada (o recién creada en esta transacción).
     */
    private void applyPayment(Invoice invoice, PaymentRequest request) {
        applyPayment(invoice, request, LocalDate.now(), Instant.now());
    }

    /**
     * @param entryDate fecha del asiento contable del pago
     * @param paidAt    instante que se guarda como fecha/hora del recibo
     */
    private void applyPayment(Invoice invoice, PaymentRequest request, LocalDate entryDate, Instant paidAt) {
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance = invoice.getTotal().subtract(invoice.getPaidAmount());
        if (amount.compareTo(balance) > 0) {
            throw new APIException("El pago (L " + amount + ") excede el saldo pendiente (L " + balance + ").");
        }

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setPaymentNumber(nextPaymentNumber(requireLaboratoryId()));
        payment.setPaidAt(paidAt);
        payment.setAmount(amount);
        payment.setMethod(request.getMethod());
        String reference = request.getReference() != null ? request.getReference().trim() : null;
        payment.setReference(reference != null && !reference.isEmpty() ? reference : null);
        payment.setReceivedByUsername(currentUsername());
        payment = paymentRepository.save(payment);

        journalService.post(
                entryDate,
                "Pago factura Nº " + invoice.getInvoiceNumber() + " (recibo Nº " + payment.getPaymentNumber() + ")",
                JournalSourceType.PAGO,
                payment.getId(),
                List.of(
                        LinePlan.debit(journalService.cashOrBank(request.getMethod()), amount),
                        LinePlan.credit(journalService.systemAccount(SystemAccountKey.CUENTAS_POR_COBRAR), amount)));

        invoice.setPaidAmount(invoice.getPaidAmount().add(amount).setScale(2, RoundingMode.HALF_UP));
        invoice.setStatus(statusFor(invoice));
        invoiceRepository.save(invoice);
    }

    /**
     * Partida de la emisión. La factura siempre carga Cuentas por cobrar (aunque
     * sea de contado: el pago inmediato la salda en su propia partida), así el
     * mapeo es uno solo para contado, crédito y abonos parciales.
     */
    private void postIssueEntry(Invoice invoice, LocalDate entryDate) {
        // Ingresos se acredita por el bruto y toda rebaja va a Descuentos sobre
        // ventas: descuento por edad, regalías de línea y el cierre negociado.
        // Así el asiento cuadra (total + descuentos = subtotal) y el reporte
        // muestra cuánto se dejó de cobrar en el período.
        BigDecimal totalDiscount = invoice.getSubtotal().subtract(invoice.getTotal());

        // Solo se agregan líneas con monto: el diario rechaza líneas en cero. Una
        // factura de cortesía (total 0) no genera cuenta por cobrar; si además el
        // bruto es 0 no hay movimiento contable y no se asienta nada.
        List<LinePlan> lines = new ArrayList<>();
        if (invoice.getTotal().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(LinePlan.debit(journalService.systemAccount(SystemAccountKey.CUENTAS_POR_COBRAR), invoice.getTotal()));
        }
        if (totalDiscount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(LinePlan.debit(journalService.systemAccount(SystemAccountKey.DESCUENTOS_VENTAS), totalDiscount));
        }
        if (invoice.getSubtotal().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(LinePlan.credit(journalService.systemAccount(SystemAccountKey.INGRESOS_SERVICIOS), invoice.getSubtotal()));
        }
        if (lines.size() < 2) return;

        journalService.post(
                entryDate,
                "Factura Nº " + invoice.getInvoiceNumber() + " — " + invoice.getCustomerName(),
                JournalSourceType.FACTURA,
                invoice.getId(),
                lines);
    }

    private InvoiceStatus statusFor(Invoice invoice) {
        if (invoice.getPaidAmount().compareTo(invoice.getTotal()) >= 0) return InvoiceStatus.PAGADA;
        if (invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) return InvoiceStatus.PARCIAL;
        return InvoiceStatus.PENDIENTE;
    }

    /**
     * Entrega el siguiente correlativo de recibo del laboratorio de forma
     * atómica; ver {@link PaymentCounter}.
     */
    private Long nextPaymentNumber(Long laboratoryId) {
        PaymentCounter counter = paymentCounterRepository.findById(laboratoryId)
                .orElseGet(() -> {
                    PaymentCounter created = new PaymentCounter();
                    created.setLaboratoryId(laboratoryId);
                    created.setNextNumber(1L);
                    return created;
                });
        Long number = counter.getNextNumber();
        counter.setNextNumber(number + 1);
        paymentCounterRepository.save(counter);
        return number;
    }

    private Invoice findInvoice(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "invoiceId", invoiceId));
    }

    private String joinAddress(Laboratory laboratory) {
        String a1 = laboratory.getAddress1() != null ? laboratory.getAddress1().trim() : "";
        String a2 = laboratory.getAddress2() != null ? laboratory.getAddress2().trim() : "";
        if (a1.isEmpty()) return a2.isEmpty() ? null : a2;
        return a2.isEmpty() ? a1 : a1 + ", " + a2;
    }

    private Long requireLaboratoryId() {
        Long laboratoryId = TenantContext.getLaboratoryId();
        if (laboratoryId == null) {
            throw new APIException("No hay un laboratorio asociado a la sesión actual");
        }
        return laboratoryId;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private InvoiceDTO toDTO(Invoice invoice, boolean includePayments) {
        List<InvoiceItemDTO> itemDTOs = (invoice.getItems() == null ? List.<InvoiceItem>of() : invoice.getItems())
                .stream()
                .map(i -> new InvoiceItemDTO(i.getId(), i.getTestId(), null, i.getTestName(),
                        i.listPriceOrPrice(), i.getPrice(),
                        i.getQuantity() != null ? i.getQuantity() : BigDecimal.ONE,
                        i.getItemType() != null ? i.getItemType() : InvoiceItemType.EXAMEN,
                        i.amount()))
                .toList();
        List<InvoiceOrder> invoiceOrders = invoice.getInvoiceOrders() == null
                ? List.of() : invoice.getInvoiceOrders();
        List<InvoiceOrderDTO> orderDTOs = invoiceOrders.stream()
                .map(io -> new InvoiceOrderDTO(io.getOrder().getId(), io.getOrder().getOrderNumber(),
                        io.getCustomer() != null ? io.getCustomer().getId() : null, io.getPatientName(),
                        io.getAgeDiscountKind(),
                        io.getAgeDiscountKind() != null ? io.getAgeDiscountKind().getLabel() : null,
                        io.getAgePercent(), io.getChargedAmount(), io.getAgeDiscountAmount()))
                .toList();
        // La forma vieja (una orden por factura) sigue llenándose cuando hay
        // exactamente una, para las pantallas y clientes que la leen.
        LabOrder singleOrder = invoiceOrders.size() == 1 ? invoiceOrders.get(0).getOrder() : null;
        List<PaymentDTO> paymentDTOs = null;
        if (includePayments) {
            paymentDTOs = paymentRepository.findByInvoiceIdOrderByPaidAtAsc(invoice.getId()).stream()
                    .map(p -> new PaymentDTO(p.getId(), p.getPaymentNumber(), p.getPaidAt(), p.getAmount(),
                            p.getMethod(), p.getMethod().getLabel(), p.getReference(), p.getReceivedByUsername(),
                            p.isAnnulled(), p.getAnnulledAt(), p.getAnnulledByUsername(), p.getAnnulmentReason()))
                    .toList();
        }
        // Tramo null con descuento: las órdenes mezclan tramos (ver Invoice.discountKind).
        AgeDiscountKind kind = invoice.getDiscountKind() != null
                ? invoice.getDiscountKind()
                : (invoice.getDiscountAmount() != null && invoice.getDiscountAmount().signum() > 0
                        ? null : AgeDiscountKind.NONE);
        BigDecimal chargedTotal = (invoice.getItems() == null ? List.<InvoiceItem>of() : invoice.getItems())
                .stream()
                .map(InvoiceItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = invoice.getStatus() == InvoiceStatus.ANULADA
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : invoice.getTotal().subtract(invoice.getPaidAmount()).setScale(2, RoundingMode.HALF_UP);
        return new InvoiceDTO(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getCai(),
                invoice.getCaiRangeFrom(),
                invoice.getCaiRangeTo(),
                invoice.getCaiExpirationDate(),
                invoice.getLabName(),
                invoice.getLabTaxName(),
                invoice.getLabRtn(),
                invoice.getLabAddress(),
                invoice.getLabTaxAddress(),
                invoice.getLabPhone(),
                invoice.getLabEmail(),
                invoice.getLabHeadline(),
                invoice.getLabFooterNote(),
                invoice.getLabPacNumber(),
                invoice.getLabRegExonerado(),
                invoice.getLabRegSag(),
                invoice.getLabOrdenCompraExenta(),
                singleOrder != null ? singleOrder.getId() : null,
                singleOrder != null ? singleOrder.getOrderNumber() : null,
                invoice.getCustomer() != null ? invoice.getCustomer().getId() : null,
                // Solo el id del proxy LAZY: pedirle el nombre lo cargaría y
                // convertiría el listado en un N+1. El nombre a mostrar ya es el
                // customerName congelado, que va en la línea de abajo.
                invoice.getBillingClient() != null ? invoice.getBillingClient().getId() : null,
                invoice.getCustomerName(),
                invoice.getCustomerRtn(),
                invoice.getPatientName(),
                invoice.getIssuedAt(),
                invoice.getIssuedByUsername(),
                displayName(invoice.getIssuedByUsername()),
                invoice.getStatus(),
                invoice.getStatus().getLabel(),
                invoice.getSaleCondition(),
                invoice.getSaleCondition().getLabel(),
                kind,
                discountLabel(kind, invoice.getDiscountAmount()),
                invoice.getDiscountPercent(),
                invoice.getSubtotal(),
                // Las regalías de línea no se guardan aparte: son la diferencia
                // entre el bruto y lo que suman las líneas cobradas.
                invoice.getSubtotal().subtract(chargedTotal).setScale(2, RoundingMode.HALF_UP),
                invoice.getDiscountAmount(),
                invoice.getOtherDiscountAmount() != null
                        ? invoice.getOtherDiscountAmount()
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                invoice.getTotal(),
                invoice.getPaidAmount(),
                balance,
                amountInWordsConverter.toLempiras(invoice.getTotal()),
                invoice.getAnnulledAt(),
                invoice.getAnnulledByUsername(),
                invoice.getAnnulmentReason(),
                itemDTOs,
                paymentDTOs,
                orderTags(invoiceOrders),
                orderDTOs);
    }

    /**
     * Etiquetas de las órdenes de la factura, sin repetir, para poder distinguir de
     * un vistazo lo del convenio en el listado; una factura sin órdenes no tiene.
     * Se resuelven por lotes gracias al @BatchSize de Invoice.invoiceOrders y de
     * LabOrder.tags, así que una página de facturas no dispara una consulta por cada una.
     */
    private List<OrderTagDTO> orderTags(List<InvoiceOrder> invoiceOrders) {
        Map<Long, OrderTagDTO> tags = new java.util.LinkedHashMap<>();
        for (InvoiceOrder io : invoiceOrders) {
            if (io.getOrder().getTags() == null) continue;
            io.getOrder().getTags().forEach(t ->
                    tags.putIfAbsent(t.getId(), new OrderTagDTO(t.getId(), t.getName(), t.getColor(), null)));
        }
        return List.copyOf(tags.values());
    }
}
