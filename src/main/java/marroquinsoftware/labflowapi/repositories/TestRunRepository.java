package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.TestRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TestRunRepository extends JpaRepository<TestRun, Long> {
    List<TestRun> findByTest_IdOrderByRunNumberAsc(Long testId);
    Optional<TestRun> findTopByTest_IdOrderByRunNumberDesc(Long testId);

    // Todas las corridas de todos los exámenes de una orden en una sola consulta.
    // El detalle de orden pedía /tests/{id}/runs una vez por examen (N requests al
    // API y N consultas a Postgres); esto colapsa esas N en 1. Los fetch join traen
    // examen, resultados y parámetro de una vez para no reintroducir un N+1 al mapear
    // los DTO. El filtro por @TenantId (laboratory_id) lo sigue aplicando Hibernate.
    @Query("SELECT DISTINCT r FROM TestRun r "
            + "JOIN FETCH r.test t "
            + "LEFT JOIN FETCH r.results res "
            + "LEFT JOIN FETCH res.parameter "
            + "WHERE t.order.id = :orderId "
            + "ORDER BY t.id ASC, r.runNumber ASC")
    List<TestRun> findByOrderIdWithResults(@Param("orderId") Long orderId);
}
