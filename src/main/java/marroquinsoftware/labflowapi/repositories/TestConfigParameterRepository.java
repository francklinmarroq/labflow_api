package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.TestConfigParameter;
import marroquinsoftware.labflowapi.model.TestConfigParameterId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestConfigParameterRepository
        extends JpaRepository<TestConfigParameter, TestConfigParameterId> {

    /**
     * ¿Algún perfil sigue usando este parámetro? Se consulta al eliminar un examen
     * para no borrar un parámetro que otro perfil reutiliza (los parámetros pueden
     * compartirse entre perfiles).
     */
    boolean existsByParameter_Id(Long parameterId);
}
