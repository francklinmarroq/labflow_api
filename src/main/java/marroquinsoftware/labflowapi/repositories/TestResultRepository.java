package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {
    List<TestResult> findByTestRun_Id(Long testRunId);
}
