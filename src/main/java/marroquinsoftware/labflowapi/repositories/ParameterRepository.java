package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Parameter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParameterRepository extends JpaRepository<Parameter, Long> {
    Parameter findByName(String name);
}
