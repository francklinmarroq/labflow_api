package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.TestConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestConfigRepository extends JpaRepository<TestConfig, Long> {
    TestConfig findByTestName(String testName);
    Page<TestConfig> findByActive(boolean active, Pageable pageable);
}
