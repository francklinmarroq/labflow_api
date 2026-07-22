package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    // El listado con filtro opcional de fechas se arma con
    // BillingSpecifications.expenses() y se ejecuta con findAll(spec, pageable);
    // ahí está explicado por qué no se escribe como @Query.
}
