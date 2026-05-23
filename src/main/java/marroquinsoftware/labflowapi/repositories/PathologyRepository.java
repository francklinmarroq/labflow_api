package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Pathology;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PathologyRepository extends JpaRepository<Pathology, Long> {
    Pathology findByName(String name);
}
