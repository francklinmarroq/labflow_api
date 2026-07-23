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
    @Autowired private CaiNumberService caiNumberService;
    @Autowired private AgeDiscountCalculator ageDiscountCalculator;
    @Autowired private InvoiceTotalsCalculator invoiceTotalsCalculator;
    @Autowired private AmountInWordsConverter amountInWordsConverter;
    @Autowired private JournalService journalService;

    @Override
    public InvoicePreviewDTO previewInvoice(Long orderId) {
        Long laboratoryId = requireLaboratoryId();
        Laboratory laboratory = laboratoryRepository.findById(laboratoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", "id", laboratoryId));
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("LabOrder", "orderId", orderId));

        List<InvoiceItemDTO> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (LabTest labTest : order.getTests() == null ? List.<LabTest>of() : order.getTests()) {
            Test test = labTest.getTest();
            if (test == null) continue;
            BigDecimal price = test.getPrice() != null
                    ? test.getPrice().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            // labTestId es la llave con la que el mostrador manda un precio
            // especial para esta línea al emitir.
            items.add(new InvoiceItemDTO(null, test.getId(), labTest.getId(), test.getName(), price, price));
            subtotal = subtotal.add(price);
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        Customer customer = order.getCustomer();
        AgeDiscountDTO discount = ageDiscountCalculator.discountFor(customer.getAgeInDays(), laboratory);
        BigDecimal discountAmount = subtotal
                .multiply(discount.getPercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        Invoice existing = invoiceRepository
                .findFirstByOrderIdAndStatusNotOrderByIssuedAtDesc(orderId, InvoiceStatus.ANULADA)
                .orElse(null);

        return new InvoicePreviewDTO(
                order.getId(),
                order.getOrderNumber(),
                customer.getId(),
                customer.getName(),
                customer.getTaxNumber(),
                discount.getKind(),
                discount.getLabel(),
                discount.getPercent(),
                subtotal,
                discountAmount,
                subtotal.subtract(discountAmount),
                items,
                existing != null ? existing.getId() : null,
                existing != null ? existing.getInvoiceNumber() : null);
    }

    @Override
    @Transactional
    public InvoiceDTO createInvoice(InvoiceRequest request) {
        Long laboratoryId = requireLaboratoryId();

        // El lock sobre el laboratorio serializa por tenant la numeración CAI y,
        // de paso, el chequeo de doble facturación de la orden.
        Laboratory laboratory = laboratoryRepository.findWithLockById(laboratoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", "id", laboratoryId));

        LabOrder order = labOrderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("LabOrder", "orderId", request.getOrderId()));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new APIException("No se puede facturar una orden cancelada.");
        }
        if (order.getTests() == null || order.getTests().isEmpty()) {
            throw new APIException("La orden no tiene exámenes para facturar.");
        }
        invoiceRepository.findFirstByOrderIdAndStatusNotOrderByIssuedAtDesc(order.getId(), InvoiceStatus.ANULADA)
                .ifPresent(existing -> {
                    throw new APIException("Esta orden ya tiene una factura emitida (Nº "
                            + existing.getInvoiceNumber() + ").");
                });

        Invoice invoice = new Invoice();

        // Precios especiales que manda el mostrador, indexados por el examen de
        // la orden al que aplican (regalías, promociones, precio negociado).
        Map<Long, BigDecimal> specialPrices = new HashMap<>();
        for (InvoiceItemPriceDTO adjustment : request.getItemPrices() == null
                ? List.<InvoiceItemPriceDTO>of() : request.getItemPrices()) {
            specialPrices.put(adjustment.getLabTestId(),
                    adjustment.getPrice().setScale(2, RoundingMode.HALF_UP));
        }

        // Los ítems congelan nombre y precio del catálogo vigente, igual que en
        // cotizaciones: la factura no cambia si mañana suben las tarifas. Si la
        // línea lleva precio especial, se guardan ambos para que la factura
        // impresa muestre cuánto costaba y cuánto se cobró.
        List<InvoiceItem> items = new ArrayList<>();
        for (LabTest labTest : order.getTests()) {
            Test test = labTest.getTest();
            if (test == null) continue;
            BigDecimal listPrice = test.getPrice() != null
                    ? test.getPrice().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal charged = specialPrices.getOrDefault(labTest.getId(), listPrice);
            if (charged.compareTo(listPrice) > 0) {
                throw new APIException("El precio de «" + test.getName() + "» (L " + charged
                        + ") no puede superar el de catálogo (L " + listPrice + ").");
            }
            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setTestId(test.getId());
            item.setTestName(test.getName());
            item.setListPrice(listPrice);
            item.setPrice(charged);
            items.add(item);
        }
        if (items.isEmpty()) {
            throw new APIException("La orden no tiene exámenes para facturar.");
        }
        invoice.setItems(items);

        Customer customer = order.getCustomer();
        AgeDiscountDTO discount = ageDiscountCalculator.discountFor(customer.getAgeInDays(), laboratory);
        InvoiceTotalsCalculator.Totals totals = invoiceTotalsCalculator.compute(
                items.stream().map(InvoiceItem::getListPrice).toList(),
                items.stream().map(InvoiceItem::getPrice).toList(),
                discount.getPercent(),
                request.getTotal());
        BigDecimal subtotal = totals.subtotal();
        BigDecimal discountAmount = totals.ageDiscount();
        BigDecimal total = totals.total();

        invoice.setOrder(order);
        invoice.setCustomer(customer);
        invoice.setCustomerName(customer.getName());
        String rtn = request.getCustomerRtn() != null ? request.getCustomerRtn().trim() : null;
        invoice.setCustomerRtn(rtn != null && !rtn.isEmpty() ? rtn : customer.getTaxNumber());

        // Porcentaje realmente aplicado del tramo de edad. El descuento por edad
        // es un techo: si en mostrador se rebaja menos que la regla, el monto se
        // recorta y el % debe recortarse igual, para no imprimir "4ta edad 20%"
        // junto a un monto que en realidad es un 10%. Con la regla completa da el
        // mismo % configurado; el excedente ya va aparte como otros descuentos.
        BigDecimal charged = subtotal.subtract(totals.itemDiscount());
        BigDecimal effectivePercent = charged.compareTo(BigDecimal.ZERO) > 0
                ? discountAmount.multiply(BigDecimal.valueOf(100)).divide(charged, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        invoice.setDiscountKind(discount.getKind());
        invoice.setDiscountPercent(effectivePercent);
        invoice.setSubtotal(subtotal);
        invoice.setDiscountAmount(discountAmount);
        invoice.setOtherDiscountAmount(totals.otherDiscount());
        invoice.setTotal(total);
        invoice.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        // Número fiscal y snapshot del CAI y del emisor con los que se imprimió.
        CaiNumberService.IssuedCaiNumber issued = caiNumberService.next(laboratory);
        laboratoryRepository.save(laboratory);
        invoice.setInvoiceNumber(issued.invoiceNumber());
        invoice.setCai(issued.cai());
        invoice.setCaiRangeFrom(issued.rangeFrom());
        invoice.setCaiRangeTo(issued.rangeTo());
        invoice.setCaiExpirationDate(issued.expirationDate());
        invoice.setLabName(laboratory.getName());
        invoice.setLabRtn(laboratory.getRtn());
        invoice.setLabAddress(joinAddress(laboratory));
        invoice.setLabPhone(laboratory.getPhone());
        invoice.setLabHeadline(laboratory.getInvoiceHeadline());
        invoice.setLabFooterNote(laboratory.getInvoiceFooterNote());
        invoice.setLabPacNumber(laboratory.getPacNumber());
        invoice.setLabRegExonerado(laboratory.getRegExonerado());
        invoice.setLabRegSag(laboratory.getRegSag());
        invoice.setLabOrdenCompraExenta(laboratory.getOrdenCompraExenta());

        // Fecha de emisión: hoy por defecto, o la que indique el operador para
        // antedatar. La hora se conserva realista para las de hoy; para una fecha
        // pasada se fija a mediodía, así ninguna zona horaria la corre de día al
        // imprimir. El asiento y el pago inicial usan esta misma fecha.
        LocalDate today = LocalDate.now(LAB_ZONE);
        LocalDate issueDate = request.getIssueDate() != null ? request.getIssueDate() : today;
        if (issueDate.isAfter(today)) {
            throw new APIException("La fecha de la factura no puede ser futura.");
        }
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
    public InvoiceResponse getAllInvoices(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                          InvoiceStatus status, Long orderId, LocalDate from, LocalDate to,
                                          String search) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        // El rango es [from 00:00, to+1 00:00) en hora de Honduras; el límite
        // superior exclusivo (ver BillingSpecifications) incluye todo el día "to".
        Instant fromInstant = from != null ? from.atStartOfDay(LAB_ZONE).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(LAB_ZONE).toInstant() : null;
        Page<Invoice> page = invoiceRepository.findAll(
                BillingSpecifications.invoices(status, orderId, fromInstant, toInstant, search), pageable);
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

        // Cargos y abonos activos, mezclados en orden cronológico.
        record Event(Instant date, String description, BigDecimal charge, BigDecimal payment) {}
        List<Event> events = new ArrayList<>();
        BigDecimal totalInvoiced = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (Invoice invoice : invoiceRepository.findByCustomerIdOrderByIssuedAtAsc(customerId)) {
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

        return new CustomerStatementDTO(customer.getId(), customer.getName(), rows,
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
                        i.listPriceOrPrice(), i.getPrice()))
                .toList();
        List<PaymentDTO> paymentDTOs = null;
        if (includePayments) {
            paymentDTOs = paymentRepository.findByInvoiceIdOrderByPaidAtAsc(invoice.getId()).stream()
                    .map(p -> new PaymentDTO(p.getId(), p.getPaymentNumber(), p.getPaidAt(), p.getAmount(),
                            p.getMethod(), p.getMethod().getLabel(), p.getReference(), p.getReceivedByUsername(),
                            p.isAnnulled(), p.getAnnulledAt(), p.getAnnulledByUsername(), p.getAnnulmentReason()))
                    .toList();
        }
        AgeDiscountKind kind = invoice.getDiscountKind() != null ? invoice.getDiscountKind() : AgeDiscountKind.NONE;
        BigDecimal chargedTotal = (invoice.getItems() == null ? List.<InvoiceItem>of() : invoice.getItems())
                .stream()
                .map(InvoiceItem::getPrice)
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
                invoice.getLabRtn(),
                invoice.getLabAddress(),
                invoice.getLabPhone(),
                invoice.getLabHeadline(),
                invoice.getLabFooterNote(),
                invoice.getLabPacNumber(),
                invoice.getLabRegExonerado(),
                invoice.getLabRegSag(),
                invoice.getLabOrdenCompraExenta(),
                invoice.getOrder() != null ? invoice.getOrder().getId() : null,
                invoice.getOrder() != null ? invoice.getOrder().getOrderNumber() : null,
                invoice.getCustomer() != null ? invoice.getCustomer().getId() : null,
                invoice.getCustomerName(),
                invoice.getCustomerRtn(),
                invoice.getIssuedAt(),
                invoice.getIssuedByUsername(),
                invoice.getStatus(),
                invoice.getStatus().getLabel(),
                invoice.getSaleCondition(),
                invoice.getSaleCondition().getLabel(),
                kind,
                kind.getLabel(),
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
                paymentDTOs);
    }
}
