package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReferralRepository extends JpaRepository<Referral, Long> {

    List<Referral> findByOrder_IdOrderByReferredAtDesc(Long orderId);

    // La "memoria" de laboratorios de destino: nombres ya usados, para autocompletar.
    // El laboratorio (tenant) lo filtra Hibernate por @TenantId.
    @Query("select distinct r.destinationLabName from Referral r "
            + "where r.destinationLabName is not null order by r.destinationLabName")
    List<String> findDistinctDestinationLabNames();
}
