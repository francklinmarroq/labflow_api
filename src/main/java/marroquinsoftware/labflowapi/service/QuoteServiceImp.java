package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.AgeDiscountDTO;
import marroquinsoftware.labflowapi.payload.QuoteDTO;
import marroquinsoftware.labflowapi.payload.QuoteItemDTO;
import marroquinsoftware.labflowapi.payload.QuoteRequest;
import marroquinsoftware.labflowapi.payload.QuoteResponse;
import marroquinsoftware.labflowapi.payload.RequoteResultDTO;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.QuoteCounterRepository;
import marroquinsoftware.labflowapi.repositories.QuoteRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import marroquinsoftware.labflowapi.repositories.UserRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuoteServiceImp implements QuoteService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * De dónde salen los precios al armar los exámenes de una cotización.
     * {@code CURRENT}: catálogo y descuento de hoy (crear y recotizar).
     * {@code KEEP_QUOTED}: lo que ya estaba cotizado se respeta (editar una vigente).
     */
    private enum PricingPolicy { CURRENT, KEEP_QUOTED }

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private AgeDiscountCalculator ageDiscountCalculator;

    @Autowired
    private QuoteCounterRepository quoteCounterRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Nombre para mostrar de quien elaboró la cotización: el nombre de la persona, o
     * el correo si no tiene nombre (usuarios antiguos) o ya no existe.
     */
    private String displayName(String username) {
        if (username == null) return null;
        return userRepository.findNameByUsernameAndLaboratoryId(username, TenantContext.getLaboratoryId())
                .filter(n -> !n.isBlank())
                .orElse(username);
    }

    @Override
    public QuoteResponse getAllQuotes(Integer pageNumber, Integer pageSize, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        // El laboratorio (tenant) lo filtra Hibernate por @TenantId.
        Page<Quote> page = quoteRepository.findAll(pageable);
        QuoteResponse response = new QuoteResponse();
        response.setContent(page.getContent().stream().map(this::toDTO).toList());
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    @Override
    public QuoteDTO getQuote(Long quoteId) {
        return toDTO(findQuote(quoteId));
    }

    @Override
    @Transactional
    public QuoteDTO createQuote(QuoteRequest request) {
        Laboratory laboratory = requireLaboratory(requireLaboratoryId());
        return toDTO(quoteRepository.save(newQuote(request, laboratory)));
    }

    @Override
    @Transactional
    public QuoteDTO updateQuote(Long quoteId, QuoteRequest request) {
        // Bloqueada hasta el commit: así una conversión en orden o una recotización
        // simultánea espera y después ve la cotización ya editada (o al revés).
        Quote quote = quoteRepository.findByIdForUpdate(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", "quoteId", quoteId));
        requireOpen(quote, "editarla");
        Laboratory laboratory = requireLaboratory(requireLaboratoryId());

        applyContent(quote, request, PricingPolicy.KEEP_QUOTED, laboratory);

        // Cambiar los días no reinicia la vigencia: se cuenta desde la emisión.
        if (request.getValidityDays() != null) {
            LocalDate validUntil = issueDate(quote).plusDays(request.getValidityDays());
            if (today().isAfter(validUntil)) {
                throw new APIException("Con " + request.getValidityDays() + " días de vigencia la cotización #"
                        + quote.getQuoteNumber() + " quedaría vencida (válida hasta el "
                        + validUntil.format(DATE_FORMAT) + "). Indique más días.");
            }
            quote.setValidityDays(request.getValidityDays());
            quote.setValidUntil(validUntil);
        }

        quote.setUpdatedAt(Instant.now());
        quote.setUpdatedByUsername(currentUsername());
        return toDTO(quoteRepository.save(quote));
    }

    @Override
    @Transactional
    public RequoteResultDTO requoteQuote(Long quoteId) {
        Quote expired = quoteRepository.findByIdForUpdate(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", "quoteId", quoteId));
        QuoteStatus status = QuoteStatus.of(expired);
        if (status == QuoteStatus.OPEN) {
            String until = expired.getValidUntil() != null
                    ? " hasta el " + expired.getValidUntil().format(DATE_FORMAT) : "";
            throw new APIException("La cotización #" + expired.getQuoteNumber() + " sigue vigente" + until
                    + "; puede editarla en lugar de recotizarla.");
        }
        if (status != QuoteStatus.EXPIRED) {
            throw closedException(expired);
        }
        Laboratory laboratory = requireLaboratory(requireLaboratoryId());

        // Solo pasan a la nueva los exámenes que siguen en el catálogo; los demás se
        // informan por nombre para que el usuario sepa qué se quedó afuera.
        List<QuoteItem> oldItems = expired.getItems() == null ? List.of() : expired.getItems();
        Set<Long> catalogIds = testRepository.findAllById(oldItems.stream()
                        .map(QuoteItem::getTestId).filter(Objects::nonNull).distinct().toList())
                .stream().map(Test::getId).collect(Collectors.toSet());
        List<Long> keptTestIds = new ArrayList<>();
        List<String> omittedTests = new ArrayList<>();
        for (QuoteItem item : oldItems) {
            if (item.getTestId() != null && catalogIds.contains(item.getTestId())) {
                keptTestIds.add(item.getTestId());
            } else {
                omittedTests.add(item.getTestName());
            }
        }
        if (keptTestIds.isEmpty()) {
            throw new APIException("Ninguno de los exámenes de la cotización #" + expired.getQuoteNumber()
                    + " sigue en el catálogo; haga una cotización nueva.");
        }

        // Mismo paciente: si es del expediente se toman sus datos actuales; si no,
        // el nombre y la edad con que se cotizó.
        QuoteRequest request = new QuoteRequest();
        if (expired.getCustomer() != null) {
            request.setCustomerId(expired.getCustomer().getId());
        } else {
            request.setPatientName(expired.getPatientName());
            request.setPatientAgeInDays(expired.getPatientAgeInDays());
        }
        request.setTestIds(keptTestIds);
        request.setNotes(expired.getNotes());

        Quote replacement = newQuote(request, laboratory);
        replacement.setRequotedFromId(expired.getId());
        replacement.setRequotedFromNumber(expired.getQuoteNumber());
        replacement = quoteRepository.save(replacement);

        expired.setRequotedById(replacement.getId());
        expired.setRequotedByNumber(replacement.getQuoteNumber());
        quoteRepository.save(expired);

        return new RequoteResultDTO(toDTO(replacement), omittedTests);
    }

    @Override
    @Transactional
    public void closeWithOrder(Long quoteId, Long orderId, Long orderNumber) {
        int updated = quoteRepository.closeWithOrder(quoteId, requireLaboratoryId(), orderId, orderNumber, today());
        if (updated == 1) return;
        // No se cerró: se relee solo para explicar por qué. La excepción revierte la
        // transacción de la orden, así que tampoco queda una orden suelta.
        Quote quote = findQuote(quoteId);
        if (QuoteStatus.of(quote) == QuoteStatus.EXPIRED) {
            throw expiredException(quote, "crear la orden");
        }
        throw closedException(quote);
    }

    @Override
    public void deleteQuote(Long quoteId) {
        quoteRepository.delete(findQuote(quoteId));
    }

    @Override
    public AgeDiscountDTO previewDiscount(Integer ageInDays) {
        return ageDiscountCalculator.discountFor(ageInDays, requireLaboratory(requireLaboratoryId()));
    }

    /**
     * Arma una cotización nueva a precios de hoy, con número, fecha, autor y
     * vigencia. La usan crear y recotizar.
     */
    private Quote newQuote(QuoteRequest request, Laboratory laboratory) {
        Quote quote = new Quote();
        quote.setItems(new ArrayList<>());
        applyContent(quote, request, PricingPolicy.CURRENT, laboratory);

        quote.setQuoteNumber(nextQuoteNumber(laboratory.getId()));
        quote.setQuotedAt(Instant.now());
        quote.setCreatedByUsername(currentUsername());

        int validityDays = request.getValidityDays() != null
                ? request.getValidityDays()
                : laboratory.effectiveQuoteValidityDays();
        quote.setValidityDays(validityDays);
        quote.setValidUntil(issueDate(quote).plusDays(validityDays));
        return quote;
    }

    /**
     * Copia a la cotización paciente, exámenes, descuento, totales y notas. Los
     * totales se calculan aquí y se guardan: la cotización es un documento de
     * precios y no debe cambiar si mañana suben las tarifas del catálogo.
     *
     * <p>Con {@code KEEP_QUOTED}, cada examen que ya estaba conserva el nombre y
     * precio cotizados (se empata por examen, así quitarlo y volverlo a marcar no
     * le cambia el precio) y el porcentaje de descuento se conserva mientras el tipo
     * de descuento del paciente no cambie.
     */
    private void applyContent(Quote quote, QuoteRequest request, PricingPolicy policy, Laboratory laboratory) {
        Map<Long, QuoteItem> quoted = new HashMap<>();
        if (policy == PricingPolicy.KEEP_QUOTED && quote.getItems() != null) {
            for (QuoteItem item : quote.getItems()) {
                if (item.getTestId() != null) quoted.putIfAbsent(item.getTestId(), item);
            }
        }

        applyPatient(quote, request);

        List<QuoteItem> items = buildItems(quote, request.getTestIds(), quoted);
        // Se vacía y se rellena la misma colección: Hibernate la vigila por
        // orphanRemoval y reemplazarla por otra lista rompe el borrado de huérfanos.
        quote.getItems().clear();
        quote.getItems().addAll(items);

        BigDecimal subtotal = items.stream()
                .map(QuoteItem::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        AgeDiscountDTO discount = ageDiscountCalculator.discountFor(quote.getPatientAgeInDays(), laboratory);
        BigDecimal percent = discount.getPercent();
        if (policy == PricingPolicy.KEEP_QUOTED
                && discount.getKind() == quote.getDiscountKind()
                && quote.getDiscountPercent() != null) {
            percent = quote.getDiscountPercent();
        }
        BigDecimal discountAmount = subtotal
                .multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        quote.setDiscountKind(discount.getKind());
        quote.setDiscountPercent(percent);
        quote.setSubtotal(subtotal);
        quote.setDiscountAmount(discountAmount);
        quote.setTotal(subtotal.subtract(discountAmount));

        String notes = request.getNotes() != null ? request.getNotes().trim() : null;
        quote.setNotes(notes != null && !notes.isEmpty() ? notes : null);
    }

    /**
     * Copia al documento el nombre y la edad con los que se cotiza. Si el paciente
     * ya está en el expediente, ambos salen de ahí (así el descuento va siempre
     * contra la edad registrada); si no, se usan los que escribió el usuario.
     */
    private void applyPatient(Quote quote, QuoteRequest request) {
        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "customerId", request.getCustomerId()));
            quote.setCustomer(customer);
            quote.setPatientName(customer.getName());
            quote.setPatientAgeInDays(customer.getAgeInDays());
            return;
        }
        String name = request.getPatientName() != null ? request.getPatientName().trim() : "";
        if (name.isEmpty()) {
            throw new APIException("Escriba el nombre del paciente o seleccione uno registrado.");
        }
        if (request.getPatientAgeInDays() == null || request.getPatientAgeInDays() <= 0) {
            throw new APIException("Indique la edad del paciente para saber si le corresponde descuento.");
        }
        // Al editar, pasar de un paciente del expediente a uno sin registrar suelta
        // el vínculo con el expediente.
        quote.setCustomer(null);
        quote.setPatientName(name);
        quote.setPatientAgeInDays(request.getPatientAgeInDays());
    }

    /**
     * Arma los exámenes en el orden recibido. Los que están en {@code quoted} copian
     * nombre y precio cotizados (aunque ya no estén en el catálogo); los demás se
     * toman del catálogo.
     */
    private List<QuoteItem> buildItems(Quote quote, List<Long> testIds, Map<Long, QuoteItem> quoted) {
        if (testIds == null || testIds.isEmpty()) {
            throw new APIException("Debe seleccionar al menos un examen para cotizar");
        }
        List<Long> fromCatalog = testIds.stream().filter(id -> !quoted.containsKey(id)).distinct().toList();
        Map<Long, Test> testsById = testRepository.findAllById(fromCatalog).stream()
                .collect(Collectors.toMap(Test::getId, Function.identity()));
        List<QuoteItem> items = new ArrayList<>();
        for (Long testId : testIds) {
            QuoteItem item = new QuoteItem();
            item.setQuote(quote);
            item.setTestId(testId);
            QuoteItem previous = quoted.get(testId);
            if (previous != null) {
                item.setTestName(previous.getTestName());
                item.setPrice(previous.getPrice());
            } else {
                Test test = testsById.get(testId);
                if (test == null) {
                    throw new APIException("Uno de los exámenes seleccionados ya no está en el catálogo. Recargue la página e intente de nuevo.");
                }
                item.setTestName(test.getName());
                item.setPrice(test.getPrice() != null ? test.getPrice().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            }
            items.add(item);
        }
        return items;
    }

    /** Solo una cotización vigente se edita o se convierte en orden. */
    private void requireOpen(Quote quote, String action) {
        QuoteStatus status = QuoteStatus.of(quote);
        if (status == QuoteStatus.EXPIRED) throw expiredException(quote, action);
        if (status != QuoteStatus.OPEN) throw closedException(quote);
    }

    private APIException expiredException(Quote quote, String action) {
        return new APIException("La cotización #" + quote.getQuoteNumber() + " venció el "
                + quote.getValidUntil().format(DATE_FORMAT) + "; recotícela para actualizar los precios antes de "
                + action + ".");
    }

    private APIException closedException(Quote quote) {
        if (quote.getOrderId() != null) {
            return new APIException("La cotización #" + quote.getQuoteNumber()
                    + " ya se convirtió en la orden #" + quote.getOrderNumber() + ".");
        }
        if (quote.getRequotedById() != null) {
            return new APIException("La cotización #" + quote.getQuoteNumber()
                    + " ya se recotizó en la cotización #" + quote.getRequotedByNumber() + ".");
        }
        return new APIException("La cotización #" + quote.getQuoteNumber() + " ya no está vigente.");
    }

    /** Fecha de emisión en hora de Honduras: desde ahí se cuenta la vigencia. */
    private LocalDate issueDate(Quote quote) {
        return quote.getQuotedAt().atZone(AppConstants.LAB_ZONE).toLocalDate();
    }

    private LocalDate today() {
        return LocalDate.now(AppConstants.LAB_ZONE);
    }

    /**
     * Entrega el siguiente correlativo del laboratorio de forma atómica; ver
     * {@link QuoteCounter}.
     */
    private Long nextQuoteNumber(Long laboratoryId) {
        QuoteCounter counter = quoteCounterRepository.findById(laboratoryId)
                .orElseGet(() -> {
                    QuoteCounter created = new QuoteCounter();
                    created.setLaboratoryId(laboratoryId);
                    created.setNextNumber(1L);
                    return created;
                });
        Long number = counter.getNextNumber();
        counter.setNextNumber(number + 1);
        quoteCounterRepository.save(counter);
        return number;
    }

    private Quote findQuote(Long quoteId) {
        return quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", "quoteId", quoteId));
    }

    private Laboratory requireLaboratory(Long laboratoryId) {
        return laboratoryRepository.findById(laboratoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", "id", laboratoryId));
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

    private QuoteDTO toDTO(Quote quote) {
        List<QuoteItemDTO> itemDTOs = (quote.getItems() == null ? List.<QuoteItem>of() : quote.getItems())
                .stream()
                .map(i -> new QuoteItemDTO(i.getId(), i.getTestId(), i.getTestName(), i.getPrice()))
                .toList();
        AgeDiscountKind kind = quote.getDiscountKind() != null ? quote.getDiscountKind() : AgeDiscountKind.NONE;
        QuoteStatus status = QuoteStatus.of(quote);
        QuoteDTO dto = new QuoteDTO();
        dto.setId(quote.getId());
        dto.setQuoteNumber(quote.getQuoteNumber());
        dto.setCustomerId(quote.getCustomer() != null ? quote.getCustomer().getId() : null);
        dto.setPatientName(quote.getPatientName());
        dto.setPatientAgeInDays(quote.getPatientAgeInDays());
        dto.setQuotedAt(quote.getQuotedAt());
        dto.setNotes(quote.getNotes());
        dto.setCreatedByUsername(quote.getCreatedByUsername());
        dto.setCreatedByName(displayName(quote.getCreatedByUsername()));
        dto.setDiscountKind(kind);
        dto.setDiscountLabel(kind.getLabel());
        dto.setDiscountPercent(quote.getDiscountPercent());
        dto.setSubtotal(quote.getSubtotal());
        dto.setDiscountAmount(quote.getDiscountAmount());
        dto.setTotal(quote.getTotal());
        dto.setItems(itemDTOs);
        dto.setStatus(status);
        dto.setStatusLabel(status.getLabel());
        dto.setValidityDays(quote.getValidityDays());
        dto.setValidUntil(quote.getValidUntil());
        dto.setOrderId(quote.getOrderId());
        dto.setOrderNumber(quote.getOrderNumber());
        dto.setRequotedById(quote.getRequotedById());
        dto.setRequotedByNumber(quote.getRequotedByNumber());
        dto.setRequotedFromId(quote.getRequotedFromId());
        dto.setRequotedFromNumber(quote.getRequotedFromNumber());
        dto.setUpdatedAt(quote.getUpdatedAt());
        dto.setUpdatedByUsername(quote.getUpdatedByUsername());
        dto.setUpdatedByName(displayName(quote.getUpdatedByUsername()));
        return dto;
    }
}
