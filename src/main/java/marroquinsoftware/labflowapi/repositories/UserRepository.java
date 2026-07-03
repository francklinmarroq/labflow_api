package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    // app_user no usa @TenantId (el login busca por username global),
    // así que el laboratorio se filtra explícitamente.
    List<User> findByLaboratoryIdOrderByUsername(Long laboratoryId);
    long countByAppRole_Id(Long roleId);
}
