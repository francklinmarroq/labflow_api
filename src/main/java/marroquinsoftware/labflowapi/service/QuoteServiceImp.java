package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.AgeDiscountDTO;
import marroquinsoftware.labflowapi.payload.QuoteDTO;
import marroquinsoftware.labflowapi.payload.QuoteItemDTO;
import marroquinsoftware.labflowapi.payload.QuoteRequest;
import marroquinsoftware.labflowapi.payload.QuoteResponse;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.QuoteCounterRepository;
import marroquinsoftware.labflowapi.repositories.QuoteRepository;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuoteServiceImp implements QuoteService {

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
        Long laboratoryId = requireLaboratoryId();

        Quote quote = new Quote();
        applyPatient(quote, request);

        List<QuoteItem> items = buildItems(quote, request.getTestIds());
        quote.setItems(items);

        // Los totales se calculan aquí y se guardan: la cotización es un documento
        // de precios y no debe cambiar si mañana suben las tarifas del catálogo.
        BigDecimal subtotal = items.stream()
                .map(QuoteItem::getPrice)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        AgeDiscountDTO discount = ageDiscountCalculator.discountFor(quote.getPatientAgeInDays(), requireLaboratory(laboratoryId));
        BigDecimal discountAmount = subtotal
                .multiply(discount.getPercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        quote.setDiscountKind(discount.getKind());
        quote.setDiscountPercent(discount.getPercent());
        quote.setSubtotal(subtotal);
        quote.setDiscountAmount(discountAmount);
        quote.setTotal(subtotal.subtract(discountAmount));

        quote.setQuoteNumber(nextQuoteNumber(laboratoryId));
        quote.setQuotedAt(Instant.now());
        quote.setCreatedByUsername(currentUsername());
        String notes = request.getNotes() != null ? request.getNotes().trim() : null;
        quote.setNotes(notes != null && !notes.isEmpty() ? notes : null);

        return toDTO(quoteRepository.save(quote));
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
        quote.setPatientName(name);
        quote.setPatientAgeInDays(request.getPatientAgeInDays());
    }

    private List<QuoteItem> buildItems(Quote quote, List<Long> testIds) {
        Map<Long, Test> testsById = testRepository.findAllById(testIds).stream()
                .collect(Collectors.toMap(Test::getId, Function.identity()));
        List<QuoteItem> items = new ArrayList<>();
        for (Long testId : testIds) {
            Test test = testsById.get(testId);
            if (test == null) {
                throw new APIException("Uno de los exámenes seleccionados ya no está en el catálogo. Recargue la página e intente de nuevo.");
            }
            QuoteItem item = new QuoteItem();
            item.setQuote(quote);
            item.setTestId(test.getId());
            item.setTestName(test.getName());
            item.setPrice(test.getPrice() != null ? test.getPrice().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            items.add(item);
        }
        return items;
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
        return new QuoteDTO(
                quote.getId(),
                quote.getQuoteNumber(),
                quote.getCustomer() != null ? quote.getCustomer().getId() : null,
                quote.getPatientName(),
                quote.getPatientAgeInDays(),
                quote.getQuotedAt(),
                quote.getNotes(),
                quote.getCreatedByUsername(),
                kind,
                kind.getLabel(),
                quote.getDiscountPercent(),
                quote.getSubtotal(),
                quote.getDiscountAmount(),
                quote.getTotal(),
                itemDTOs);
    }
}
