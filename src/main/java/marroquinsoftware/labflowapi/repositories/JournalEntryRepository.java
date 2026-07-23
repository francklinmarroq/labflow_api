package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.JournalEntry;
import marroquinsoftware.labflowapi.model.JournalSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface JournalEntryRepository
        extends JpaRepository<JournalEntry, Long>, JpaSpecificationExecutor<JournalEntry> {

    // El listado con filtros opcionales se arma con
    // BillingSpecifications.journalEntries() y se ejecuta con
    // findAll(spec, pageable); ahí está explicado por qué no se escribe como
    // @Query.

    /** Partida original de un documento, para armar su contra-asiento al anular. */
    Optional<JournalEntry> findFirstBySourceTypeAndSourceId(JournalSourceType sourceType, Long sourceId);
}
