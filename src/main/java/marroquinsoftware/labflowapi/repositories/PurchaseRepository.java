package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.LockModeType;
import marroquinsoftware.labflowapi.model.Purchase;
import marroquinsoftware.labflowapi.model.SaleCondition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Los reportes leen el proveedor de cada compra: @EntityGraph lo trae en la misma
// consulta en vez de una por compra.
public interface PurchaseRepository extends JpaRepository<Purchase, Long>, JpaSpecificationExecutor<Purchase> {

    /**
     * La compra con bloqueo de escritura, para que dos pagos simultáneos no
     * lean el mismo saldo pendiente y lo excedan entre los dos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> findWithLockById(@Param("id") Long id);

    /** Libro de compras: las vigentes del rango, en orden de fecha. */
    @EntityGraph(attributePaths = "supplier")
    List<Purchase> findByAnnulledFalseAndPurchaseDateBetweenOrderByPurchaseDateAscIdAsc(
            LocalDate from, LocalDate to);

    /** Compras vigentes de un proveedor con la condición dada hasta una fecha. */
    @EntityGraph(attributePaths = "supplier")
    List<Purchase> findBySupplierIdAndConditionAndAnnulledFalseAndPurchaseDateLessThanEqualOrderByPurchaseDateAscIdAsc(
            Long supplierId, SaleCondition condition, LocalDate to);

    /** Compras vigentes con la condición dada hasta una fecha, de todos los proveedores. */
    @EntityGraph(attributePaths = "supplier")
    List<Purchase> findByConditionAndAnnulledFalseAndPurchaseDateLessThanEqual(
            SaleCondition condition, LocalDate to);
}
