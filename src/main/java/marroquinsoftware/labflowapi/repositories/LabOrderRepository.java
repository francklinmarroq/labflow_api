package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.LabOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {
}
