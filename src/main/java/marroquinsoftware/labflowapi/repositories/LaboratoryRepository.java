package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.LockModeType;
import marroquinsoftware.labflowapi.model.Laboratory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LaboratoryRepository extends JpaRepository<Laboratory, Long> {

    /**
     * Lee el laboratorio tomando un bloqueo de escritura sobre su fila. La
     * emisión de facturas lo usa para serializar la numeración CAI (el
     * correlativo vive en los campos cai*CurrentNumber) y, de paso, el chequeo
     * de "esta orden ya tiene factura".
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Laboratory l where l.id = :id")
    Optional<Laboratory> findWithLockById(@Param("id") Long id);
}
