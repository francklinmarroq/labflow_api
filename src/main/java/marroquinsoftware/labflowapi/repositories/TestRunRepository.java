package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.TestRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TestRunRepository extends JpaRepository<TestRun, Long> {
    List<TestRun> findByTest_IdOrderByRunNumberAsc(Long testId);
    Optional<TestRun> findTopByTest_IdOrderByRunNumberDesc(Long testId);
}
