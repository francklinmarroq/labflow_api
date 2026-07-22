package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.LockModeType;
import marroquinsoftware.labflowapi.model.QuoteCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface QuoteCounterRepository extends JpaRepository<QuoteCounter, Long> {

    /**
     * Lee el contador del laboratorio tomando un bloqueo de escritura sobre la
     * fila, para serializar la asignación de correlativos entre creaciones
     * concurrentes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QuoteCounter> findById(Long laboratoryId);
}
