package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Purchase;
import marroquinsoftware.labflowapi.model.SaleCondition;
import marroquinsoftware.labflowapi.model.Supplier;
import marroquinsoftware.labflowapi.model.SupplierPayment;
import marroquinsoftware.labflowapi.payload.*;
import marroquinsoftware.labflowapi.repositories.PurchaseRepository;
import marroquinsoftware.labflowapi.repositories.SupplierPaymentRepository;
import marroquinsoftware.labflowapi.repositories.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PurchaseReportServiceImp implements PurchaseReportService {

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private SupplierPaymentRepository supplierPaymentRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Override
    @Transactional(readOnly = true)
    public PurchaseBookDTO getPurchaseBook(LocalDate from, LocalDate to) {
        List<PurchaseBookRowDTO> rows = purchaseRepository
                .findByAnnulledFalseAndPurchaseDateBetweenOrderByPurchaseDateAscIdAsc(from, to).stream()
                .map(p -> new PurchaseBookRowDTO(p.getId(), p.getPurchaseDate(), p.getFiscalNumber(), p.getCai(),
                        p.getSupplier().getRtn(), p.getSupplier().getName(), p.getExemptBase(),
                        p.getTaxedBase15(), p.getTaxedBase18(), p.getIsv15(), p.getIsv18(), p.getTotal()))
                .toList();
        return new PurchaseBookDTO(from, to, rows,
                sum(rows, PurchaseBookRowDTO::getExemptBase),
                sum(rows, PurchaseBookRowDTO::getTaxedBase15),
                sum(rows, PurchaseBookRowDTO::getTaxedBase18),
                sum(rows, PurchaseBookRowDTO::getIsv15),
                sum(rows, PurchaseBookRowDTO::getIsv18),
                sum(rows, PurchaseBookRowDTO::getTotal));
    }

    /**
     * Solo las compras a crédito cuentan: son las únicas que dejan deuda con el
     * proveedor. Una de contado nace pagada y no mueve su saldo.
     */
    @Override
    @Transactional(readOnly = true)
    public SupplierStatementDTO getSupplierStatement(Long supplierId, LocalDate from, LocalDate to) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "supplierId", supplierId));
        List<Purchase> purchases = purchaseRepository
                .findBySupplierIdAndConditionAndAnnulledFalseAndPurchaseDateLessThanEqualOrderByPurchaseDateAscIdAsc(
                        supplierId, SaleCondition.CREDITO, to);
        List<SupplierPayment> payments = supplierPaymentRepository
                .findByPurchaseSupplierIdAndAnnulledFalseAndPaymentDateLessThanEqualOrderByPaymentDateAscPaymentNumberAsc(
                        supplierId, to);

        // Saldo inicial: lo comprado menos lo pagado antes del rango.
        BigDecimal opening = BigDecimal.ZERO;
        List<SupplierStatementRowDTO> rows = new ArrayList<>();
        for (Purchase p : purchases) {
            if (p.getPurchaseDate().isBefore(from)) {
                opening = opening.add(p.getTotal());
            } else {
                rows.add(new SupplierStatementRowDTO(p.getPurchaseDate(), "COMPRA",
                        "Compra Nº " + p.getFiscalNumber(), p.getId(), p.getTotal(), BigDecimal.ZERO, null));
            }
        }
        for (SupplierPayment pay : payments) {
            if (pay.getPaymentDate().isBefore(from)) {
                opening = opening.subtract(pay.getAmount());
            } else {
                rows.add(new SupplierStatementRowDTO(pay.getPaymentDate(), "PAGO",
                        "Pago recibo Nº " + pay.getPaymentNumber() + ", compra Nº " + pay.getPurchase().getFiscalNumber(),
                        pay.getPurchase().getId(), BigDecimal.ZERO, pay.getAmount(), null));
            }
        }
        // En orden cronológico; el mismo día, la compra antes que su pago.
        rows.sort(Comparator.comparing(SupplierStatementRowDTO::getDate)
                .thenComparing(r -> "PAGO".equals(r.getType())));

        BigDecimal running = opening;
        BigDecimal charges = BigDecimal.ZERO, paid = BigDecimal.ZERO;
        for (SupplierStatementRowDTO row : rows) {
            running = running.add(row.getCharge()).subtract(row.getPayment());
            row.setBalance(running);
            charges = charges.add(row.getCharge());
            paid = paid.add(row.getPayment());
        }
        return new SupplierStatementDTO(supplier.getId(), supplier.getName(), supplier.getRtn(), from, to,
                opening, rows, charges, paid, running);
    }

    /**
     * El saldo de cada compra a crédito es su total menos los pagos vigentes
     * hechos hasta la fecha de corte; los días se cuentan desde la fecha de la
     * compra. Así la antigüedad a una fecha pasada muestra lo que se debía ese día.
     */
    @Override
    @Transactional(readOnly = true)
    public PayablesAgingDTO getPayablesAging(LocalDate date) {
        Map<Long, BigDecimal> paidByPurchase = new HashMap<>();
        for (SupplierPayment pay : supplierPaymentRepository.findByAnnulledFalseAndPaymentDateLessThanEqual(date)) {
            paidByPurchase.merge(pay.getPurchase().getId(), pay.getAmount(), BigDecimal::add);
        }

        Map<Long, PayablesAgingRowDTO> bySupplier = new LinkedHashMap<>();
        for (Purchase p : purchaseRepository.findByConditionAndAnnulledFalseAndPurchaseDateLessThanEqual(
                SaleCondition.CREDITO, date)) {
            BigDecimal balance = p.getTotal().subtract(paidByPurchase.getOrDefault(p.getId(), BigDecimal.ZERO));
            if (balance.signum() <= 0) continue;
            Supplier s = p.getSupplier();
            PayablesAgingRowDTO row = bySupplier.computeIfAbsent(s.getId(), id -> new PayablesAgingRowDTO(
                    id, s.getName(), s.getRtn(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO));
            long days = ChronoUnit.DAYS.between(p.getPurchaseDate(), date);
            if (days <= 30) row.setDays0To30(row.getDays0To30().add(balance));
            else if (days <= 60) row.setDays31To60(row.getDays31To60().add(balance));
            else if (days <= 90) row.setDays61To90(row.getDays61To90().add(balance));
            else row.setOver90(row.getOver90().add(balance));
            row.setTotal(row.getTotal().add(balance));
        }

        List<PayablesAgingRowDTO> rows = new ArrayList<>(bySupplier.values());
        rows.sort(Comparator.comparing(PayablesAgingRowDTO::getSupplierName, String.CASE_INSENSITIVE_ORDER));
        return new PayablesAgingDTO(date, rows,
                sum(rows, PayablesAgingRowDTO::getDays0To30),
                sum(rows, PayablesAgingRowDTO::getDays31To60),
                sum(rows, PayablesAgingRowDTO::getDays61To90),
                sum(rows, PayablesAgingRowDTO::getOver90),
                sum(rows, PayablesAgingRowDTO::getTotal));
    }

    private <T> BigDecimal sum(List<T> rows, java.util.function.Function<T, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
