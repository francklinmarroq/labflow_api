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
import java.util.Collection;
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

    // "¿Qué factura cubre esta orden?" ya no se pregunta aquí: la factura puede
    // cubrir varias órdenes o ninguna, y esa relación vive en InvoiceOrderRepository.

    // El listado con filtros opcionales se arma con
    // BillingSpecifications.invoices() y se ejecuta con findAll(spec, pageable);
    // ahí está explicado por qué no se escribe como @Query.

    /**
     * Facturas con saldo abierto (cuentas por cobrar). El mapeo a DTO lee
     * customer.id y billingClient.id de cada fila; el {@link EntityGraph} trae los
     * dos en la misma consulta de la página (son to-one, no multiplican filas)
     * para evitar el N+1 que dispararía el @ManyToOne EAGER al recorrerla. Las
     * órdenes de cada factura se leen por lotes (@BatchSize de invoiceOrders).
     */
    @EntityGraph(attributePaths = {"customer", "billingClient"})
    @Query("select i from Invoice i where i.status in (marroquinsoftware.labflowapi.model.InvoiceStatus.PENDIENTE, marroquinsoftware.labflowapi.model.InvoiceStatus.PARCIAL)")
    Page<Invoice> findReceivables(Pageable pageable);

    @Query("select coalesce(sum(i.total - i.paidAmount), 0) from Invoice i where i.status in (marroquinsoftware.labflowapi.model.InvoiceStatus.PENDIENTE, marroquinsoftware.labflowapi.model.InvoiceStatus.PARCIAL)")
    java.math.BigDecimal totalReceivable();

    /** Facturas de un paciente para el estado de cuenta, más antiguas primero. */
    List<Invoice> findByCustomerIdOrderByIssuedAtAsc(Long customerId);

    /**
     * Facturas emitidas a un cliente de facturación, más antiguas primero, para su
     * estado de cuenta. Espejo del de pacientes, sobre la asociación nueva.
     */
    List<Invoice> findByBillingClientIdOrderByIssuedAtAsc(Long billingClientId);

    /**
     * ¿Este cliente de facturación tiene alguna factura? Las ANULADA cuentan: su
     * identidad sigue impresa en un documento fiscal, así que tampoco se puede
     * borrar al cliente al que apuntan.
     */
    boolean existsByBillingClientId(Long billingClientId);

    /**
     * Saldo abierto por cliente de facturación: filas
     * [billingClientId, nombre vivo del cliente, cantidad de facturas, suma de saldos].
     *
     * <p>Una sola consulta agregada, no una por cliente. El join interno con
     * {@code billingClient} deja fuera las facturas emitidas a un paciente, y el
     * nombre sale de la tabla del catálogo (no del congelado en la factura)
     * porque esto es una lista de trabajo para cobrar, no un documento fiscal.
     */
    @Query("""
            select bc.id, bc.name, count(i.id), coalesce(sum(i.total - i.paidAmount), 0)
            from Invoice i
              join i.billingClient bc
            where i.status in (marroquinsoftware.labflowapi.model.InvoiceStatus.PENDIENTE,
                               marroquinsoftware.labflowapi.model.InvoiceStatus.PARCIAL)
            group by bc.id, bc.name
            """)
    List<Object[]> receivablesByBillingClient();

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

    /**
     * Filas [testName, price, quantity] de las líneas de las facturas emitidas en
     * el rango, para el desglose por examen. Una línea agrupada vale su cantidad,
     * no 1: diez hemogramas en una línea son diez exámenes vendidos.
     */
    @Query("""
            select ii.testName, ii.price, ii.quantity
            from InvoiceItem ii
            where ii.invoice.issuedAt >= :from and ii.invoice.issuedAt < :to
              and ii.invoice.status <> marroquinsoftware.labflowapi.model.InvoiceStatus.ANULADA
            """)
    List<Object[]> salesItemRows(@Param("from") Instant from, @Param("to") Instant to);

    // --- Registro de ventas detallado (incluye las ANULADA, que salen en cero) ---
    // Proyecciones y no entidades: cargar Invoice/InvoiceOrder arrastraría sus
    // to-one EAGER fila por fila.

    /**
     * Filas [id, issuedAt, invoiceNumber, status, customerName, discountAmount,
     * otherDiscountAmount, total] de todas las facturas emitidas en el rango,
     * anuladas incluidas, por fecha y número.
     */
    @Query("""
            select i.id, i.issuedAt, i.invoiceNumber, i.status, i.customerName,
                   i.discountAmount, i.otherDiscountAmount, i.total
            from Invoice i
            where i.issuedAt >= :from and i.issuedAt < :to
            order by i.issuedAt asc, i.invoiceNumber asc
            """)
    List<Object[]> registerInvoiceRows(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Filas [invoiceId, testId, itemType, testName, listPrice, price, quantity] de
     * las líneas de esas mismas facturas, en el orden en que se guardaron.
     */
    @Query("""
            select ii.invoice.id, ii.testId, ii.itemType, ii.testName, ii.listPrice, ii.price, ii.quantity
            from InvoiceItem ii
            where ii.invoice.issuedAt >= :from and ii.invoice.issuedAt < :to
            order by ii.invoice.id asc, ii.id asc
            """)
    List<Object[]> registerItemRows(@Param("from") Instant from, @Param("to") Instant to);

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
