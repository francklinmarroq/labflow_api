package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceIdOrderByPaidAtAsc(Long invoiceId);
}
