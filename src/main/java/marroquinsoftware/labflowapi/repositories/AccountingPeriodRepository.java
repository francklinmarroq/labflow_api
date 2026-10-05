package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.AccountingPeriod;
import marroquinsoftware.labflowapi.model.AccountingPeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    // El laboratorio (tenant) lo filtra Hibernate por @TenantId en todas estas.
    // A igual fecha de fin (un período reabierto y vuelto a cerrar) va primero el
    // más reciente, que es el que vale.
    List<AccountingPeriod> findAllByOrderByEndDateDescIdDesc();

    /** Período cerrado que contiene la fecha, si lo hay: el que bloquea una partida. */
    @Query("""
            select p from AccountingPeriod p
            where p.status = :status and p.startDate <= :date and p.endDate >= :date
            """)
    List<AccountingPeriod> findContaining(@Param("status") AccountingPeriodStatus status,
                                          @Param("date") LocalDate date);

    /** Períodos con el estado dado cuyo rango se traslapa con [from, to]. */
    @Query("""
            select p from AccountingPeriod p
            where p.status = :status and p.startDate <= :to and p.endDate >= :from
            order by p.startDate
            """)
    List<AccountingPeriod> findOverlapping(@Param("status") AccountingPeriodStatus status,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    Optional<AccountingPeriod> findFirstByStatusOrderByEndDateDesc(AccountingPeriodStatus status);
}
