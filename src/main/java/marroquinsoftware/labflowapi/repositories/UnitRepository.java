package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, Long> {
    Unit findByUnitSymbol(String unitSymbol);
}
