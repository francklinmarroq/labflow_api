package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.ReferenceRange;
import marroquinsoftware.labflowapi.model.Sex;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReferenceRangeRepository extends JpaRepository<ReferenceRange, Long> {

    Page<ReferenceRange> findByParameterId(Long parameterId, Pageable pageable);

    @Query("SELECT r FROM ReferenceRange r WHERE r.parameter.id = :parameterId " +
           "AND (r.sex IS NULL OR r.sex = :sex) " +
           "AND (r.ageRange IS NULL " +
           "     OR ((r.ageRange.minAgeDays IS NULL OR r.ageRange.minAgeDays <= :ageDays) " +
           "     AND (r.ageRange.maxAgeDays IS NULL OR r.ageRange.maxAgeDays >= :ageDays)))")
    List<ReferenceRange> findApplicable(@Param("parameterId") Long parameterId,
                                        @Param("sex") Sex sex,
                                        @Param("ageDays") Integer ageDays);
}
