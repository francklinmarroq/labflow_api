package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.LockModeType;
import marroquinsoftware.labflowapi.model.Invoice;
import marroquinsoftware.labflowapi.model.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    /**
     * Lee la factura con bloqueo de escritura, para serializar pagos
     * concurrentes sobre el mismo saldo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findWithLockById(@Param("id") Long id);

    /** ¿La orden ya tiene una factura viva (no anulada)? */
    boolean existsByOrderIdAndStatusNot(Long orderId, InvoiceStatus status);

    Optional<Invoice> findFirstByOrderIdAndStatusNotOrderByIssuedAtDesc(Long orderId, InvoiceStatus status);

    // El listado con filtros opcionales se arma con
    // BillingSpecifications.invoices() y se ejecuta con findAll(spec, pageable);
    // ahí está explicado por qué no se escribe como @Query.

    /** Facturas con saldo abierto (cuentas por cobrar). */
    @Query("select i from Invoice i where i.status in (marroquinsoftware.labflowapi.model.InvoiceStatus.PENDIENTE, marroquinsoftware.labflowapi.model.InvoiceStatus.PARCIAL)")
    Page<Invoice> findReceivables(Pageable pageable);

    @Query("select coalesce(sum(i.total - i.paidAmount), 0) from Invoice i where i.status in (marroquinsoftware.labflowapi.model.InvoiceStatus.PENDIENTE, marroquinsoftware.labflowapi.model.InvoiceStatus.PARCIAL)")
    java.math.BigDecimal totalReceivable();

    /** Facturas de un cliente para el estado de cuenta, más antiguas primero. */
    List<Invoice> findByCustomerIdOrderByIssuedAtAsc(Long customerId);
}
