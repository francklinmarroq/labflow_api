package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.LockModeType;
import marroquinsoftware.labflowapi.model.PaymentCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PaymentCounterRepository extends JpaRepository<PaymentCounter, Long> {

    /**
     * Lee el contador del laboratorio tomando un bloqueo de escritura sobre la
     * fila, para serializar la asignación de correlativos entre pagos
     * concurrentes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentCounter> findById(Long laboratoryId);
}
