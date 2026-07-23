package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    /**
     * Movimiento neto (débitos - créditos) de una cuenta antes de una fecha:
     * el saldo inicial del mayor. Null cuando no hay movimientos.
     */
    @Query("""
            select sum(l.debit - l.credit) from JournalLine l
            where l.account.id = :accountId and l.entry.entryDate < :before
            """)
    BigDecimal netBefore(@Param("accountId") Long accountId, @Param("before") LocalDate before);

    /** Movimientos de una cuenta en un rango de fechas, en orden de partida. */
    @Query("""
            select l from JournalLine l join fetch l.entry e
            where l.account.id = :accountId
              and e.entryDate >= :from and e.entryDate <= :to
            order by e.entryDate, e.entryNumber, l.lineOrder
            """)
    List<JournalLine> movements(@Param("accountId") Long accountId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    /**
     * Totales de débitos y créditos por cuenta en un rango de fechas, para la
     * balanza de comprobación. Cada fila: [accountId, sumaDebe, sumaHaber].
     */
    @Query("""
            select l.account.id, sum(l.debit), sum(l.credit) from JournalLine l
            where l.entry.entryDate >= :from and l.entry.entryDate <= :to
            group by l.account.id
            """)
    List<Object[]> totalsByAccount(@Param("from") LocalDate from, @Param("to") LocalDate to);

    boolean existsByAccountId(Long accountId);
}
