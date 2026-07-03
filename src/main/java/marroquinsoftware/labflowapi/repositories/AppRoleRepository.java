package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppRoleRepository extends JpaRepository<AppRole, Long> {
    // El laboratorio (tenant) lo filtra Hibernate por @TenantId.
    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
