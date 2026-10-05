package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.LockModeType;
import marroquinsoftware.labflowapi.model.SupplierPaymentCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface SupplierPaymentCounterRepository extends JpaRepository<SupplierPaymentCounter, Long> {

    /** Con bloqueo de escritura, para que dos pagos simultáneos no compartan recibo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SupplierPaymentCounter> findById(Long laboratoryId);
}
