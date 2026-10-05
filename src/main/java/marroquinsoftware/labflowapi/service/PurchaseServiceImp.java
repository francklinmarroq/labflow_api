package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.*;
import marroquinsoftware.labflowapi.repositories.AccountRepository;
import marroquinsoftware.labflowapi.repositories.PurchaseRepository;
import marroquinsoftware.labflowapi.repositories.PurchaseSpecifications;
import marroquinsoftware.labflowapi.repositories.SupplierPaymentRepository;
import marroquinsoftware.labflowapi.repositories.SupplierRepository;
import marroquinsoftware.labflowapi.service.JournalService.LinePlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

@Service
public class PurchaseServiceImp implements PurchaseService {

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SupplierPaymentRepository supplierPaymentRepository;

    @Autowired
    private JournalService journalService;

    @Override
    @Transactional
    public PurchaseDTO registerPurchase(PurchaseRequest request) {
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "supplierId", request.getSupplierId()));
        if (!supplier.isActive()) {
            throw new APIException("El proveedor «" + supplier.getName() + "» está desactivado.");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new APIException("La compra necesita al menos una línea.");
        }
        if (request.getCondition() == SaleCondition.CONTADO && request.getMethod() == null) {
            throw new APIException("Indique la forma de pago de la compra de contado.");
        }
        // El diario la rechazaría de todos modos al postear; comprobarlo antes
        // evita guardar un documento que la transacción tendría que deshacer.
        journalService.requireOpenPeriod(request.getPurchaseDate());

        Purchase purchase = new Purchase();
        purchase.setSupplier(supplier);
        purchase.setPurchaseDate(request.getPurchaseDate());
        purchase.setFiscalNumber(request.getFiscalNumber().trim());
        purchase.setCai(blankToNull(request.getCai()));
        purchase.setCaiDeadline(request.getCaiDeadline());
        purchase.setCondition(request.getCondition());
        purchase.setMethod(request.getCondition() == SaleCondition.CONTADO ? request.getMethod() : null);
        purchase.setNotes(blankToNull(request.getNotes()));

        // Cada línea se redondea por separado y el documento suma las líneas ya
        // redondeadas: así el desglose es reproducible en la vista previa del
        // cliente, que hace la misma cuenta.
        List<PurchaseLine> lines = new ArrayList<>();
        BigDecimal exempt = BigDecimal.ZERO, taxed15 = BigDecimal.ZERO, taxed18 = BigDecimal.ZERO;
        BigDecimal isv15 = BigDecimal.ZERO, isv18 = BigDecimal.ZERO;
        int position = 0;
        for (PurchaseLineRequest req : request.getLines()) {
            position++;
            if (req.getQuantity().signum() <= 0 || req.getUnitPrice().signum() <= 0) {
                throw new APIException("La línea " + position + " necesita cantidad y precio unitario mayores que cero.");
            }
            Account account = lineAccount(req.getAccountId(), position);

            BigDecimal base = req.getQuantity().multiply(req.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal isv = base.multiply(req.getIsvRate().getRate()).setScale(2, RoundingMode.HALF_UP);
            switch (req.getIsvRate()) {
                case EXENTO -> exempt = exempt.add(base);
                case GRAVADO_15 -> { taxed15 = taxed15.add(base); isv15 = isv15.add(isv); }
                case GRAVADO_18 -> { taxed18 = taxed18.add(base); isv18 = isv18.add(isv); }
            }

            PurchaseLine line = new PurchaseLine();
            line.setPurchase(purchase);
            line.setDescription(req.getDescription().trim());
            line.setQuantity(req.getQuantity().setScale(3, RoundingMode.HALF_UP));
            line.setUnitPrice(req.getUnitPrice().setScale(2, RoundingMode.HALF_UP));
            line.setIsvRate(req.getIsvRate());
            line.setAccount(account);
            line.setBase(base);
            line.setIsv(isv);
            line.setLineOrder(position);
            lines.add(line);
        }
        BigDecimal total = exempt.add(taxed15).add(taxed18).add(isv15).add(isv18);

        purchase.setLines(lines);
        purchase.setExemptBase(exempt);
        purchase.setTaxedBase15(taxed15);
        purchase.setTaxedBase18(taxed18);
        purchase.setIsv15(isv15);
        purchase.setIsv18(isv18);
        purchase.setTotal(total);
        // Una de contado nace pagada: el asiento abona Caja o Bancos de una vez.
        boolean cash = request.getCondition() == SaleCondition.CONTADO;
        purchase.setPaidAmount(cash ? total : BigDecimal.ZERO);
        purchase.setStatus(cash ? PurchaseStatus.PAGADA : PurchaseStatus.PENDIENTE);
        purchase.setCreatedAt(Instant.now());
        purchase.setCreatedByUsername(currentUsername());
        purchase = purchaseRepository.save(purchase);

        // Una línea de cargo por línea de compra (la cuenta de cada una), el ISV
        // a su cuenta propia, y el abono a Caja/Bancos o a Cuentas por pagar. La
        // fecha del asiento es la de la compra, así que una compra fechada en un
        // período cerrado la rechaza el diario y nada de esto se guarda.
        List<LinePlan> plan = new ArrayList<>();
        for (PurchaseLine line : lines) {
            plan.add(LinePlan.debit(line.getAccount(), line.getBase()));
        }
        BigDecimal isvTotal = isv15.add(isv18);
        if (isvTotal.signum() > 0) {
            plan.add(LinePlan.debit(journalService.systemAccount(SystemAccountKey.ISV_NO_RECUPERABLE_COMPRAS), isvTotal));
        }
        Account creditAccount = cash
                ? journalService.cashOrBank(purchase.getMethod())
                : journalService.systemAccount(SystemAccountKey.CUENTAS_POR_PAGAR);
        plan.add(LinePlan.credit(creditAccount, total));
        journalService.post(purchase.getPurchaseDate(),
                "Compra Nº " + purchase.getFiscalNumber() + " — " + supplier.getName(),
                JournalSourceType.COMPRA, purchase.getId(), plan);

        return toDTO(purchase, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseResponse getPurchases(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                         LocalDate from, LocalDate to, Long supplierId,
                                         SaleCondition condition, PurchaseStatus status) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Page<Purchase> page = purchaseRepository.findAll(
                PurchaseSpecifications.purchases(from, to, supplierId, condition, status),
                PageRequest.of(pageNumber, pageSize, sort));
        PurchaseResponse response = new PurchaseResponse();
        response.setContent(page.getContent().stream().map(p -> toDTO(p, false)).toList());
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseDTO getPurchase(Long purchaseId) {
        return toDTO(findPurchase(purchaseId), true);
    }

    @Override
    @Transactional
    public PurchaseDTO annulPurchase(Long purchaseId, String reason) {
        Purchase purchase = purchaseRepository.findWithLockById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase", "purchaseId", purchaseId));
        if (purchase.isAnnulled()) {
            throw new APIException("Esta compra ya está anulada.");
        }
        // Anularla con pagos vigentes dejaría Cuentas por pagar en negativo: los
        // pagos se anulan primero, y cada uno devuelve su saldo.
        if (supplierPaymentRepository.existsByPurchaseIdAndAnnulledFalse(purchaseId)) {
            throw new APIException("La compra tiene pagos vigentes: anúlelos primero y luego anule la compra.");
        }

        journalService.reverse(
                journalService.findSourceEntry(JournalSourceType.COMPRA, purchase.getId()),
                JournalSourceType.ANULACION_COMPRA,
                purchase.getId(),
                "Anulación de compra Nº " + purchase.getFiscalNumber() + " — " + purchase.getSupplier().getName());

        purchase.setAnnulled(true);
        purchase.setStatus(PurchaseStatus.ANULADA);
        purchase.setAnnulledAt(Instant.now());
        purchase.setAnnulledByUsername(currentUsername());
        purchase.setAnnulmentReason(reason != null ? reason.trim() : null);
        return toDTO(purchaseRepository.save(purchase), true);
    }

    /**
     * La cuenta de una línea: activa, de GASTO o ACTIVO, y no de sistema. Las de
     * sistema las mueve solo el código (Caja, Cuentas por pagar, el ISV): cargarles
     * una compra a mano descuadraría lo que esas cuentas significan.
     */
    private Account lineAccount(Long accountId, int position) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "accountId", accountId));
        String name = account.getCode() + " — " + account.getName();
        if (!account.isActive()) {
            throw new APIException("La cuenta " + name + " de la línea " + position + " está desactivada.");
        }
        if (account.getSystemKey() != null) {
            throw new APIException("La cuenta " + name + " de la línea " + position
                    + " es una cuenta del sistema y no admite compras.");
        }
        if (account.getType() != AccountType.GASTO && account.getType() != AccountType.ACTIVO) {
            throw new APIException("La cuenta " + name + " de la línea " + position
                    + " no es de gastos ni de activos.");
        }
        return account;
    }

    private Purchase findPurchase(Long purchaseId) {
        return purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase", "purchaseId", purchaseId));
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    /** Estado de una compra a crédito según lo pagado; las de contado nacen PAGADA. */
    static PurchaseStatus creditStatus(BigDecimal paid, BigDecimal total) {
        if (paid.signum() == 0) return PurchaseStatus.PENDIENTE;
        return paid.compareTo(total) < 0 ? PurchaseStatus.PARCIAL : PurchaseStatus.PAGADA;
    }

    /**
     * @param detail con líneas, pagos y partida; el listado los omite.
     *
     * <p>Público a propósito: {@link SupplierPaymentServiceImp} lo llama a través
     * del proxy transaccional de este servicio, y un proxy de Spring solo delega
     * en el objeto real los métodos públicos; uno de paquete correría sobre el
     * proxy, con los repositorios en null.
     */
    public PurchaseDTO toDTO(Purchase purchase, boolean detail) {
        List<PurchaseLineDTO> lines = List.of();
        List<SupplierPaymentDTO> payments = List.of();
        Long entryId = null, entryNumber = null;
        if (detail) {
            lines = purchase.getLines().stream()
                    .map(l -> new PurchaseLineDTO(l.getId(), l.getDescription(), l.getQuantity(), l.getUnitPrice(),
                            l.getIsvRate(), l.getIsvRate().getLabel(), l.getAccount().getId(),
                            l.getAccount().getCode(), l.getAccount().getName(), l.getBase(), l.getIsv()))
                    .toList();
            // Una compra recién creada en esta misma transacción todavía no tiene id
            // de pagos que buscar; las demás traen los suyos.
            payments = purchase.getId() == null ? List.of()
                    : supplierPaymentRepository.findByPurchaseIdOrderByPaymentNumberAsc(purchase.getId()).stream()
                    .map(SupplierPaymentServiceImp::toDTO)
                    .toList();
            JournalEntry entry = journalService.findSourceEntryIfExists(JournalSourceType.COMPRA, purchase.getId())
                    .orElse(null);
            if (entry != null) {
                entryId = entry.getId();
                entryNumber = entry.getEntryNumber();
            }
        }
        Supplier supplier = purchase.getSupplier();
        return new PurchaseDTO(
                purchase.getId(),
                supplier.getId(),
                supplier.getName(),
                supplier.getRtn(),
                purchase.getPurchaseDate(),
                purchase.getFiscalNumber(),
                purchase.getCai(),
                purchase.getCaiDeadline(),
                purchase.getCondition(),
                purchase.getCondition().getLabel(),
                purchase.getMethod(),
                purchase.getMethod() != null ? purchase.getMethod().getLabel() : null,
                purchase.getNotes(),
                purchase.getExemptBase(),
                purchase.getTaxedBase15(),
                purchase.getTaxedBase18(),
                purchase.getIsv15(),
                purchase.getIsv18(),
                purchase.getTotal(),
                purchase.getPaidAmount(),
                purchase.isAnnulled() ? BigDecimal.ZERO : purchase.getTotal().subtract(purchase.getPaidAmount()),
                purchase.getStatus(),
                purchase.getStatus().getLabel(),
                lines,
                payments,
                entryId,
                entryNumber,
                purchase.getCreatedAt(),
                purchase.getCreatedByUsername(),
                purchase.isAnnulled(),
                purchase.getAnnulledAt(),
                purchase.getAnnulledByUsername(),
                purchase.getAnnulmentReason());
    }
}
