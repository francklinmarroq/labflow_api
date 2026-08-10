package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.LockModeType;
import marroquinsoftware.labflowapi.model.Invoice;
import marroquinsoftware.labflowapi.model.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * Facturas con saldo abierto (cuentas por cobrar). El mapeo a DTO lee
     * order.id/order.orderNumber y customer.id de cada fila; el {@link EntityGraph}
     * trae ambos en la misma consulta de la página (son to-one, no multiplican
     * filas) para evitar el N+1 que dispararía el @ManyToOne EAGER al recorrerla.
     */
    @EntityGraph(attributePaths = {"order", "customer"})
    @Query("select i from Invoice i where i.status in (marroquinsoftware.labflowapi.model.InvoiceStatus.PENDIENTE, marroquinsoftware.labflowapi.model.InvoiceStatus.PARCIAL)")
    Page<Invoice> findReceivables(Pageable pageable);

    @Query("select coalesce(sum(i.total - i.paidAmount), 0) from Invoice i where i.status in (marroquinsoftware.labflowapi.model.InvoiceStatus.PENDIENTE, marroquinsoftware.labflowapi.model.InvoiceStatus.PARCIAL)")
    java.math.BigDecimal totalReceivable();

    /** Facturas de un cliente para el estado de cuenta, más antiguas primero. */
    List<Invoice> findByCustomerIdOrderByIssuedAtAsc(Long customerId);

    // --- Reportería de ventas (todas excluyen las facturas ANULADA) ---
    // El laboratorio (tenant) lo filtra Hibernate por @TenantId; el rango es
    // [from, to) con el límite superior exclusivo (la conversión vive en el
    // servicio). Se devuelven filas crudas y la agregación por día/mes/cliente se
    // hace en el servicio (en zona horaria de Honduras), evitando funciones de
    // fecha propias de cada motor (H2 en tests vs PostgreSQL en prod).

    /** Filas [issuedAt, subtotal, discountAmount, otherDiscountAmount, total, customerName] de las facturas emitidas en el rango. */
    @Query("""
            select i.issuedAt, i.subtotal, i.discountAmount, i.otherDiscountAmount, i.total, i.customerName
            from Invoice i
            where i.issuedAt >= :from and i.issuedAt < :to
              and i.status <> marroquinsoftware.labflowapi.model.InvoiceStatus.ANULADA
            """)
    List<Object[]> salesInvoiceRows(@Param("from") Instant from, @Param("to") Instant to);

    /** Filas [testName, price] de las líneas de las facturas emitidas en el rango, para el desglose por examen. */
    @Query("""
            select ii.testName, ii.price
            from InvoiceItem ii
            where ii.invoice.issuedAt >= :from and ii.invoice.issuedAt < :to
              and ii.invoice.status <> marroquinsoftware.labflowapi.model.InvoiceStatus.ANULADA
            """)
    List<Object[]> salesItemRows(@Param("from") Instant from, @Param("to") Instant to);

    /** Facturas emitidas y monto vendido por usuario emisor en el rango: filas [issuedByUsername, count, sum(total)]. */
    @Query("""
            select i.issuedByUsername, count(i.id), coalesce(sum(i.total), 0)
            from Invoice i
            where i.issuedAt >= :from and i.issuedAt < :to
              and i.status <> marroquinsoftware.labflowapi.model.InvoiceStatus.ANULADA
            group by i.issuedByUsername
            """)
    List<Object[]> salesByUser(@Param("from") Instant from, @Param("to") Instant to);
}
