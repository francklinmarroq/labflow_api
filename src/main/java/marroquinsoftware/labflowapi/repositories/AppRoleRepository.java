package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.AppRole;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@code AppRole} no usa {@code @TenantId} (se carga en la autenticación, sin
 * tenant), así que el laboratorio se filtra explícitamente en cada consulta.
 */
@Repository
public interface AppRoleRepository extends JpaRepository<AppRole, Long> {
    List<AppRole> findByLaboratoryId(Long laboratoryId, Sort sort);

    boolean existsByLaboratoryIdAndName(Long laboratoryId, String name);

    boolean existsByLaboratoryIdAndNameAndIdNot(Long laboratoryId, String name, Long id);
}
