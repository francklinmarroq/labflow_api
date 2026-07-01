package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.LockModeType;
import marroquinsoftware.labflowapi.model.LabOrderCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface LabOrderCounterRepository extends JpaRepository<LabOrderCounter, Long> {

    /**
     * Lee el contador del laboratorio tomando un bloqueo de escritura sobre la
     * fila, para serializar la asignación de folios entre creaciones concurrentes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LabOrderCounter> findById(Long laboratoryId);
}
