package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Account;
import marroquinsoftware.labflowapi.model.SystemAccountKey;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // El laboratorio (tenant) lo filtra Hibernate por @TenantId en todas estas.
    Optional<Account> findBySystemKey(SystemAccountKey systemKey);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Account> findByActiveTrue(Sort sort);
}
