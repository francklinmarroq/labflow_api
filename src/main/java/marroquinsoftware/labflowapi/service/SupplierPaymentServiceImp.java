package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.PurchaseDTO;
import marroquinsoftware.labflowapi.payload.SupplierPaymentDTO;
import marroquinsoftware.labflowapi.payload.SupplierPaymentRequest;
import marroquinsoftware.labflowapi.repositories.PurchaseRepository;
import marroquinsoftware.labflowapi.repositories.SupplierPaymentCounterRepository;
import marroquinsoftware.labflowapi.repositories.SupplierPaymentRepository;
import marroquinsoftware.labflowapi.service.JournalService.LinePlan;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
public class SupplierPaymentServiceImp implements SupplierPaymentService {

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private SupplierPaymentRepository supplierPaymentRepository;

    @Autowired
    private SupplierPaymentCounterRepository supplierPaymentCounterRepository;

    @Autowired
    private JournalService journalService;

    @Autowired
    private PurchaseServiceImp purchaseService;

    @Override
    @Transactional
    public PurchaseDTO registerPayment(Long purchaseId, SupplierPaymentRequest request) {
        // Con bloqueo: dos pagos simultáneos no deben leer el mismo saldo.
        Purchase purchase = purchaseRepository.findWithLockById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase", "purchaseId", purchaseId));
        if (purchase.getStatus() != PurchaseStatus.PENDIENTE && purchase.getStatus() != PurchaseStatus.PARCIAL) {
            throw new APIException("La compra no tiene saldo pendiente: está "
                    + purchase.getStatus().getLabel().toLowerCase() + ".");
        }
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance = purchase.getTotal().subtract(purchase.getPaidAmount());
        if (amount.signum() <= 0) {
            throw new APIException("El monto del pago debe ser mayor que cero.");
        }
        if (amount.compareTo(balance) > 0) {
            throw new APIException("El pago (L " + amount + ") excede el saldo pendiente (L " + balance + ").");
        }

        // Antes de tomar recibo ni guardar el pago; ver PurchaseServiceImp.
        journalService.requireOpenPeriod(request.getPaymentDate());

        SupplierPayment payment = new SupplierPayment();
        payment.setPurchase(purchase);
        payment.setPaymentNumber(nextPaymentNumber(requireLaboratoryId()));
        payment.setPaymentDate(request.getPaymentDate());
        payment.setAmount(amount);
        payment.setMethod(request.getMethod());
        String reference = request.getReference() != null ? request.getReference().trim() : null;
        payment.setReference(reference != null && !reference.isEmpty() ? reference : null);
        payment.setCreatedAt(Instant.now());
        payment.setCreatedByUsername(currentUsername());
        payment = supplierPaymentRepository.save(payment);

        // Con la fecha del pago: uno fechado en un período cerrado lo rechaza el
        // diario y la transacción completa se revierte, saldo incluido.
        journalService.post(payment.getPaymentDate(),
                "Pago a proveedor " + purchase.getSupplier().getName() + ", compra Nº "
                        + purchase.getFiscalNumber() + " (recibo Nº " + payment.getPaymentNumber() + ")",
                JournalSourceType.PAGO_PROVEEDOR, payment.getId(),
                List.of(
                        LinePlan.debit(journalService.systemAccount(SystemAccountKey.CUENTAS_POR_PAGAR), amount),
                        LinePlan.credit(journalService.cashOrBank(payment.getMethod()), amount)));

        purchase.setPaidAmount(purchase.getPaidAmount().add(amount));
        purchase.setStatus(PurchaseServiceImp.creditStatus(purchase.getPaidAmount(), purchase.getTotal()));
        purchaseRepository.save(purchase);
        return purchaseService.toDTO(purchase, true);
    }

    @Override
    @Transactional
    public PurchaseDTO annulPayment(Long paymentId, String reason) {
        SupplierPayment payment = supplierPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("SupplierPayment", "paymentId", paymentId));
        if (payment.isAnnulled()) {
            throw new APIException("Este pago ya está anulado.");
        }
        Purchase purchase = purchaseRepository.findWithLockById(payment.getPurchase().getId()).orElseThrow();

        journalService.reverse(
                journalService.findSourceEntry(JournalSourceType.PAGO_PROVEEDOR, payment.getId()),
                JournalSourceType.ANULACION_PAGO_PROVEEDOR,
                payment.getId(),
                "Anulación del pago a proveedor recibo Nº " + payment.getPaymentNumber()
                        + ", compra Nº " + purchase.getFiscalNumber());

        payment.setAnnulled(true);
        payment.setAnnulledAt(Instant.now());
        payment.setAnnulledByUsername(currentUsername());
        payment.setAnnulmentReason(reason != null ? reason.trim() : null);
        supplierPaymentRepository.save(payment);

        purchase.setPaidAmount(purchase.getPaidAmount().subtract(payment.getAmount()));
        purchase.setStatus(PurchaseServiceImp.creditStatus(purchase.getPaidAmount(), purchase.getTotal()));
        purchaseRepository.save(purchase);
        return purchaseService.toDTO(purchase, true);
    }

    /** Siguiente recibo del laboratorio de forma atómica; ver {@link SupplierPaymentCounter}. */
    private Long nextPaymentNumber(Long laboratoryId) {
        SupplierPaymentCounter counter = supplierPaymentCounterRepository.findById(laboratoryId)
                .orElseGet(() -> {
                    SupplierPaymentCounter created = new SupplierPaymentCounter();
                    created.setLaboratoryId(laboratoryId);
                    created.setNextNumber(1L);
                    return created;
                });
        Long number = counter.getNextNumber();
        counter.setNextNumber(number + 1);
        supplierPaymentCounterRepository.save(counter);
        return number;
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

    static SupplierPaymentDTO toDTO(SupplierPayment payment) {
        return new SupplierPaymentDTO(
                payment.getId(),
                payment.getPaymentNumber(),
                payment.getPurchase().getId(),
                payment.getPaymentDate(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getMethod().getLabel(),
                payment.getReference(),
                payment.getCreatedAt(),
                payment.getCreatedByUsername(),
                payment.isAnnulled(),
                payment.getAnnulledAt(),
                payment.getAnnulledByUsername(),
                payment.getAnnulmentReason());
    }
}
