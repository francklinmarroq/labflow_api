package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /** Listado de gastos con filtro opcional de rango de fechas. */
    @Query("""
            select x from Expense x
            where (:from is null or x.expenseDate >= :from)
              and (:to is null or x.expenseDate <= :to)
            """)
    Page<Expense> search(@Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         Pageable pageable);
}
