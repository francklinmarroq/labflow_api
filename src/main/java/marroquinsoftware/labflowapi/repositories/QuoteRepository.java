package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.LockModeType;
import marroquinsoftware.labflowapi.model.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    // El laboratorio (tenant) lo filtra Hibernate por @TenantId.
    List<Quote> findByCustomer_IdOrderByQuotedAtDesc(Long customerId);

    /**
     * Carga la cotización bloqueando su fila hasta el final de la transacción. La
     * usan editar y recotizar para que no se crucen entre ellas ni con la
     * conversión en orden (ver {@link #closeWithOrder}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quote q where q.id = :id")
    Optional<Quote> findByIdForUpdate(@Param("id") Long id);

    /**
     * Cierra la cotización con la orden indicada solo si sigue vigente. Es un UPDATE
     * condicional y no leer-verificar-guardar para que dos conversiones simultáneas
     * no ganen las dos: PostgreSQL vuelve a evaluar el WHERE después de esperar el
     * bloqueo de la fila. El laboratorio va explícito en vez de confiar en que
     * {@code @TenantId} aplique a las mutaciones masivas.
     *
     * @return filas actualizadas: 1 si se cerró, 0 si no existe o ya no está vigente
     */
    @Modifying
    @Query("""
            update Quote q set q.orderId = :orderId, q.orderNumber = :orderNumber
            where q.id = :id and q.laboratoryId = :laboratoryId
              and q.orderId is null and q.requotedById is null
              and (q.validUntil is null or q.validUntil >= :today)
            """)
    int closeWithOrder(@Param("id") Long id,
                       @Param("laboratoryId") Long laboratoryId,
                       @Param("orderId") Long orderId,
                       @Param("orderNumber") Long orderNumber,
                       @Param("today") LocalDate today);
}
