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

    // Versión por lote: la pantalla de detalle/impresión de una orden necesita las
    // corridas de TODOS sus exámenes a la vez. Pedirlas de a un examen costaba una
    // llamada HTTP por examen (y contra una API con piso de ~0.7 s por request eso
    // pesa). Este LEFT JOIN FETCH trae corridas + resultados + parámetro en una sola
    // consulta, evitando además el N+1 de la colección de resultados por corrida.
    // El orden (examen, número de corrida) permite agrupar por examen en el cliente
    // preservando el mismo orden que devolvía el endpoint por examen.
    @Query("SELECT DISTINCT r FROM TestRun r " +
           "LEFT JOIN FETCH r.results res " +
           "LEFT JOIN FETCH res.parameter " +
           "WHERE r.test.order.id = :orderId " +
           "ORDER BY r.test.id ASC, r.runNumber ASC")
    List<TestRun> findByOrderIdWithResults(@Param("orderId") Long orderId);
}
