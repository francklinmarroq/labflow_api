package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {
    List<LabOrder> findByCustomer_Id(Long customerId);

    Page<LabOrder> findByLaboratory_IdAndStatusNot(Long laboratoryId, OrderStatus status, Pageable pageable);
}
