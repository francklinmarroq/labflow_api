package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.JournalSourceType;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.PaymentMethod;
import marroquinsoftware.labflowapi.model.Referral;
import marroquinsoftware.labflowapi.model.ReferralItem;
import marroquinsoftware.labflowapi.model.SystemAccountKey;
import marroquinsoftware.labflowapi.payload.ReferralDTO;
import marroquinsoftware.labflowapi.payload.ReferralItemDTO;
import marroquinsoftware.labflowapi.payload.ReferralRequest;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.ReferralRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReferralServiceImp implements ReferralService {

    @Autowired
    private ReferralRepository referralRepository;

    @Autowired
    private LabOrderRepository labOrderRepository;

    @Autowired
    private LabTestRepository labTestRepository;

    @Autowired
    private JournalService journalService;

    @Override
    @Transactional
    public ReferralDTO createReferral(Long orderId, ReferralRequest request) {
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("LabOrder", "orderId", orderId));

        // Solo se pueden remitir exámenes que pertenezcan a la orden.
        Map<Long, LabTest> testsById = labTestRepository.findByOrder_Id(orderId).stream()
                .collect(Collectors.toMap(LabTest::getId, Function.identity()));

        Referral referral = new Referral();
        referral.setOrder(order);
        referral.setDestinationLabName(request.getDestinationLabName().trim());
        String reason = request.getReason() != null ? request.getReason().trim() : null;
        referral.setReason(reason != null && !reason.isEmpty() ? reason : null);
        referral.setReferredAt(Instant.now());
        referral.setCreatedByUsername(currentUsername());
        referral.setPaymentMethod(request.getPaymentMethod());

        List<ReferralItem> items = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        for (ReferralRequest.Item requested : request.getItems()) {
            LabTest labTest = testsById.get(requested.getLabTestId());
            if (labTest == null) {
                throw new APIException("Uno de los exámenes seleccionados ya no pertenece a la orden. Recargue la página e intente de nuevo.");
            }
            BigDecimal cost = requested.getCost() != null
                    ? requested.getCost().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            ReferralItem item = new ReferralItem();
            item.setReferral(referral);
            item.setLabTestId(requested.getLabTestId());
            item.setTestName(labTest.getTest().getName());
            item.setCost(cost);
            items.add(item);
            totalCost = totalCost.add(cost);
        }
        referral.setItems(items);
        referral = referralRepository.save(referral);

        postReferralCost(referral, totalCost.setScale(2, RoundingMode.HALF_UP));

        return toDTO(referral);
    }

    /**
     * Asienta el costo de remitir: carga "Exámenes remitidos a terceros" contra
     * lo que se debe o se pagó. Si el destino no cobra (total 0), no hay nada que
     * asentar. Es lo único que la remisión aporta a la contabilidad.
     */
    private void postReferralCost(Referral referral, BigDecimal totalCost) {
        if (totalCost.compareTo(BigDecimal.ZERO) <= 0) return;

        // Sin método → queda por pagar (Cuentas por pagar); con método → se pagó
        // al momento (Caja para efectivo, Bancos para lo demás).
        var creditAccount = referral.getPaymentMethod() == null
                ? journalService.systemAccount(SystemAccountKey.CUENTAS_POR_PAGAR)
                : journalService.cashOrBank(referral.getPaymentMethod());

        journalService.post(
                LocalDate.now(),
                "Remisión a " + referral.getDestinationLabName()
                        + " — orden Nº " + referral.getOrder().getOrderNumber(),
                JournalSourceType.REMISION,
                referral.getId(),
                List.of(
                        JournalService.LinePlan.debit(
                                journalService.systemAccount(SystemAccountKey.EXAMENES_REMITIDOS), totalCost),
                        JournalService.LinePlan.credit(creditAccount, totalCost)));
    }

    @Override
    public List<ReferralDTO> getReferralsByOrder(Long orderId) {
        if (!labOrderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("LabOrder", "orderId", orderId);
        }
        return referralRepository.findByOrder_IdOrderByReferredAtDesc(orderId).stream()
                .map(this::toDTO).toList();
    }

    @Override
    public ReferralDTO getReferral(Long referralId) {
        Referral referral = referralRepository.findById(referralId)
                .orElseThrow(() -> new ResourceNotFoundException("Referral", "referralId", referralId));
        return toDTO(referral);
    }

    @Override
    public List<String> getDestinationLabNames() {
        return referralRepository.findDistinctDestinationLabNames();
    }

    @Override
    @Transactional
    public void deleteReferral(Long referralId) {
        Referral referral = referralRepository.findById(referralId)
                .orElseThrow(() -> new ResourceNotFoundException("Referral", "referralId", referralId));

        // Si la remisión había asentado un costo, se revierte con un contra-asiento
        // para que el diario quede espejo; si no tuvo costo, no hay nada que revertir.
        journalService.findSourceEntryIfExists(JournalSourceType.REMISION, referral.getId())
                .ifPresent(entry -> journalService.reverse(
                        entry,
                        JournalSourceType.ANULACION_REMISION,
                        referral.getId(),
                        "Anulación de remisión a " + referral.getDestinationLabName()));

        referralRepository.delete(referral);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private ReferralDTO toDTO(Referral referral) {
        List<ReferralItem> items = referral.getItems() == null ? List.of() : referral.getItems();
        List<ReferralItemDTO> itemDTOs = items.stream()
                .map(i -> new ReferralItemDTO(i.getId(), i.getLabTestId(), i.getTestName(),
                        i.getCost() != null ? i.getCost() : BigDecimal.ZERO))
                .toList();
        BigDecimal totalCost = items.stream()
                .map(i -> i.getCost() != null ? i.getCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new ReferralDTO(
                referral.getId(),
                referral.getOrder().getId(),
                referral.getOrder().getOrderNumber(),
                referral.getDestinationLabName(),
                referral.getReason(),
                referral.getReferredAt(),
                referral.getCreatedByUsername(),
                totalCost,
                referral.getPaymentMethod(),
                settlementLabel(referral.getPaymentMethod()),
                itemDTOs);
    }

    /** "Por pagar" cuando no hay método; "Pagado (Efectivo)" cuando se pagó al momento. */
    private String settlementLabel(PaymentMethod method) {
        return method == null ? "Por pagar" : "Pagado (" + method.getLabel() + ")";
    }
}
