package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Quote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    // El laboratorio (tenant) lo filtra Hibernate por @TenantId.
    List<Quote> findByCustomer_IdOrderByQuotedAtDesc(Long customerId);
}
