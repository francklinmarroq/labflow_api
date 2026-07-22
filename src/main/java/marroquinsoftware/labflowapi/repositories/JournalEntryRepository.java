package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.JournalEntry;
import marroquinsoftware.labflowapi.model.JournalSourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    /** Listado del diario con filtros opcionales de fecha y origen. */
    @Query("""
            select e from JournalEntry e
            where (:from is null or e.entryDate >= :from)
              and (:to is null or e.entryDate <= :to)
              and (:sourceType is null or e.sourceType = :sourceType)
            """)
    Page<JournalEntry> search(@Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              @Param("sourceType") JournalSourceType sourceType,
                              Pageable pageable);

    /** Partida original de un documento, para armar su contra-asiento al anular. */
    Optional<JournalEntry> findFirstBySourceTypeAndSourceId(JournalSourceType sourceType, Long sourceId);
}
