package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.LockModeType;
import marroquinsoftware.labflowapi.model.JournalEntryCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface JournalEntryCounterRepository extends JpaRepository<JournalEntryCounter, Long> {

    /**
     * Lee el contador del laboratorio tomando un bloqueo de escritura sobre la
     * fila, para serializar la asignación de correlativos entre partidas
     * concurrentes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<JournalEntryCounter> findById(Long laboratoryId);
}
