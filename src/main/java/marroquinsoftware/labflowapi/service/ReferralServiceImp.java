package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.Referral;
import marroquinsoftware.labflowapi.model.ReferralItem;
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

import java.time.Instant;
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

        List<ReferralItem> items = new ArrayList<>();
        for (Long labTestId : request.getLabTestIds()) {
            LabTest labTest = testsById.get(labTestId);
            if (labTest == null) {
                throw new APIException("Uno de los exámenes seleccionados ya no pertenece a la orden. Recargue la página e intente de nuevo.");
            }
            ReferralItem item = new ReferralItem();
            item.setReferral(referral);
            item.setLabTestId(labTestId);
            item.setTestName(labTest.getTest().getName());
            items.add(item);
        }
        referral.setItems(items);

        return toDTO(referralRepository.save(referral));
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
    public void deleteReferral(Long referralId) {
        Referral referral = referralRepository.findById(referralId)
                .orElseThrow(() -> new ResourceNotFoundException("Referral", "referralId", referralId));
        referralRepository.delete(referral);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private ReferralDTO toDTO(Referral referral) {
        List<ReferralItemDTO> itemDTOs = (referral.getItems() == null ? List.<ReferralItem>of() : referral.getItems())
                .stream()
                .map(i -> new ReferralItemDTO(i.getId(), i.getLabTestId(), i.getTestName()))
                .toList();
        return new ReferralDTO(
                referral.getId(),
                referral.getOrder().getId(),
                referral.getOrder().getOrderNumber(),
                referral.getDestinationLabName(),
                referral.getReason(),
                referral.getReferredAt(),
                referral.getCreatedByUsername(),
                itemDTOs);
    }
}
