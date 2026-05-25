package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Test;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRepository extends JpaRepository<Test, Long> {
    Test findByName(String name);
}
