package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.JournalLine;
import marroquinsoftware.labflowapi.model.JournalSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
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

    /**
     * Igual que {@link #totalsByAccount} pero sin las partidas de los orígenes
     * indicados. El estado de resultados y el cierre excluyen así la partida de
     * cierre y su contra-asiento, que trasladan resultados a capital y no son
     * ingresos ni gastos del período.
     */
    @Query("""
            select l.account.id, sum(l.debit), sum(l.credit) from JournalLine l
            where l.entry.entryDate >= :from and l.entry.entryDate <= :to
              and l.entry.sourceType not in :excluded
            group by l.account.id
            """)
    List<Object[]> totalsByAccountExcluding(@Param("from") LocalDate from,
                                            @Param("to") LocalDate to,
                                            @Param("excluded") Collection<JournalSourceType> excluded);

    /**
     * Totales de débitos y créditos por cuenta acumulados hasta una fecha (sin
     * límite inferior), para el balance general. Cada fila: [accountId,
     * sumaDebe, sumaHaber].
     */
    @Query("""
            select l.account.id, sum(l.debit), sum(l.credit) from JournalLine l
            where l.entry.entryDate <= :date
            group by l.account.id
            """)
    List<Object[]> totalsByAccountUpTo(@Param("date") LocalDate date);

    /**
     * Movimiento neto (débitos - créditos) de un conjunto de cuentas antes de
     * una fecha: el saldo inicial de efectivo. Null cuando no hay movimientos.
     */
    @Query("""
            select sum(l.debit - l.credit) from JournalLine l
            where l.account.id in :accountIds and l.entry.entryDate < :before
            """)
    BigDecimal netBeforeForAccounts(@Param("accountIds") Collection<Long> accountIds,
                                    @Param("before") LocalDate before);

    /**
     * Débitos y créditos de un conjunto de cuentas en un rango, agrupados por el
     * origen de la partida, para clasificar el flujo de efectivo. Cada fila:
     * [sourceType, sumaDebe, sumaHaber].
     */
    @Query("""
            select l.entry.sourceType, sum(l.debit), sum(l.credit) from JournalLine l
            where l.account.id in :accountIds
              and l.entry.entryDate >= :from and l.entry.entryDate <= :to
            group by l.entry.sourceType
            """)
    List<Object[]> totalsBySourceType(@Param("accountIds") Collection<Long> accountIds,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    boolean existsByAccountId(Long accountId);
}
