package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceIdOrderByPaidAtAsc(Long invoiceId);

    /**
     * Pagos de un conjunto de facturas en UNA sola consulta, ordenados por
     * fecha de cobro ascendente. Lo usa el estado de cuenta para no pedir los
     * pagos factura por factura (N+1): con esto trae todos los pagos del
     * titular de una vez y el servicio los agrupa por factura en memoria,
     * conservando el orden por `paidAt`. El `@TenantId` de `Payment` lo sigue
     * filtrando Hibernate, igual que en la variante por factura.
     */
    List<Payment> findByInvoiceIdInOrderByPaidAtAsc(Collection<Long> invoiceIds);

    // --- Reportería de cobros (todas excluyen los pagos anulados) ---
    // El laboratorio (tenant) lo filtra Hibernate por @TenantId; el rango es
    // [from, to) con el límite superior exclusivo (la conversión vive en el
    // servicio). La agregación por día se hace en el servicio, en zona de
    // Honduras, para no depender de funciones de fecha propias de cada motor.

    /** Filas [paidAt, amount, method] de los pagos activos recibidos en el rango. */
    @Query("""
            select p.paidAt, p.amount, p.method
            from Payment p
            where p.paidAt >= :from and p.paidAt < :to and p.annulled = false
            """)
    List<Object[]> paymentRows(@Param("from") Instant from, @Param("to") Instant to);

    /** Pagos recibidos y monto por usuario en el rango: filas [receivedByUsername, count, sum(amount)]. */
    @Query("""
            select p.receivedByUsername, count(p.id), coalesce(sum(p.amount), 0)
            from Payment p
            where p.paidAt >= :from and p.paidAt < :to and p.annulled = false
            group by p.receivedByUsername
            """)
    List<Object[]> paymentsByUser(@Param("from") Instant from, @Param("to") Instant to);
}
