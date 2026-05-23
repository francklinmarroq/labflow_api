package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.LabTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LabTestRepository extends JpaRepository<LabTest, Long> {
    List<LabTest> findByOrder_Id(Long orderId);
}
