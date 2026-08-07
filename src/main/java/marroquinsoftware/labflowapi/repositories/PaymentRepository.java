package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceIdOrderByPaidAtAsc(Long invoiceId);

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
