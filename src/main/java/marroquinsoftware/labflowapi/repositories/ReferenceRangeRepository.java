package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.ReferenceRange;
import marroquinsoftware.labflowapi.model.Sex;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReferenceRangeRepository extends JpaRepository<ReferenceRange, Long> {

    Page<ReferenceRange> findByParameterId(Long parameterId, Pageable pageable);

    // Versión por lote: la pantalla de una orden necesita los rangos de todos los
    // parámetros a la vez, y pedirlos de a uno costaba una llamada HTTP por
    // parámetro (veinte y pico en un hemograma).
    List<ReferenceRange> findByParameterIdIn(Collection<Long> parameterIds, Sort sort);

    // Filtra por edad usando los límites propios de la fila (minAgeDays/maxAgeDays)
    // si están presentes; si no, cae al grupo de edad con nombre (ageRange). El
    // LEFT JOIN evita que las filas sin ageRange se pierdan por join implícito.
    // Nota: puede devolver varias filas de una tabla por edad ("desde" solapados);
    // el reporte se queda con la que aplica (mayor "desde" ≤ edad).
    @Query("SELECT r FROM ReferenceRange r LEFT JOIN r.ageRange ar " +
           "WHERE r.parameter.id = :parameterId " +
           "AND (r.sex IS NULL OR r.sex = :sex) " +
           "AND ( " +
           "     ((r.minAgeDays IS NOT NULL OR r.maxAgeDays IS NOT NULL) " +
           "       AND (r.minAgeDays IS NULL OR r.minAgeDays <= :ageDays) " +
           "       AND (r.maxAgeDays IS NULL OR r.maxAgeDays >= :ageDays)) " +
           "     OR (r.minAgeDays IS NULL AND r.maxAgeDays IS NULL " +
           "       AND (ar IS NULL " +
           "            OR ((ar.minAgeDays IS NULL OR ar.minAgeDays <= :ageDays) " +
           "            AND (ar.maxAgeDays IS NULL OR ar.maxAgeDays >= :ageDays)))))")
    List<ReferenceRange> findApplicable(@Param("parameterId") Long parameterId,
                                        @Param("sex") Sex sex,
                                        @Param("ageDays") Integer ageDays);
}
