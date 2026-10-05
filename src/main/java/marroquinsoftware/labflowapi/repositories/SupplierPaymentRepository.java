package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.SupplierPayment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    List<SupplierPayment> findByPurchaseIdOrderByPaymentNumberAsc(Long purchaseId);

    boolean existsByPurchaseIdAndAnnulledFalse(Long purchaseId);

    /** Pagos vigentes a un proveedor hasta una fecha, para su estado de cuenta. */
    @EntityGraph(attributePaths = {"purchase", "purchase.supplier"})
    List<SupplierPayment> findByPurchaseSupplierIdAndAnnulledFalseAndPaymentDateLessThanEqualOrderByPaymentDateAscPaymentNumberAsc(
            Long supplierId, LocalDate to);

    /** Pagos vigentes de todos los proveedores hasta una fecha, para la antigüedad. */
    @EntityGraph(attributePaths = {"purchase", "purchase.supplier"})
    List<SupplierPayment> findByAnnulledFalseAndPaymentDateLessThanEqual(LocalDate to);
}
