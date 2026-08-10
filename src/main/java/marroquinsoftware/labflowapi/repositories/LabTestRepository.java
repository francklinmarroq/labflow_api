package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.LabTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LabTestRepository extends JpaRepository<LabTest, Long> {
    List<LabTest> findByOrder_Id(Long orderId);

    /** ¿El examen del catálogo está usado en alguna orden? Bloquea su eliminación. */
    boolean existsByTest_Id(Long testId);

    /**
     * Conteo de exámenes realizados por tipo de examen en un rango de fechas,
     * para el reporte de volumen de exámenes. Se cuenta cada LabTest cuya orden
     * fue solicitada dentro del rango [from, to) y no está cancelada. El
     * laboratorio (tenant) lo filtra Hibernate por @TenantId sobre LabTest y
     * LabOrder; el límite superior es exclusivo (la conversión de fechas vive en
     * el servicio). Devuelve filas [testId, testName, area, count] ordenadas de
     * mayor a menor volumen.
     */
    @Query("""
            select t.test.id, t.test.name, t.test.area, count(t.id)
            from LabTest t
            where t.order.requestedAt >= :from and t.order.requestedAt < :to
              and t.order.status <> marroquinsoftware.labflowapi.model.OrderStatus.CANCELLED
            group by t.test.id, t.test.name, t.test.area
            order by count(t.id) desc, t.test.name asc
            """)
    List<Object[]> testsVolume(@Param("from") Instant from, @Param("to") Instant to);
}
