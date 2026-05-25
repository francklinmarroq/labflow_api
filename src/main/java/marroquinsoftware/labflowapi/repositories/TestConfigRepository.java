package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.TestConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestConfigRepository extends JpaRepository<TestConfig, Long> {
    TestConfig findByName(String name);
    Page<TestConfig> findByActive(boolean active, Pageable pageable);
    Page<TestConfig> findByTestId(Long testId, Pageable pageable);
}
