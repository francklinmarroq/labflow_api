package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.criteria.Predicate;
import marroquinsoftware.labflowapi.model.Expense;
import marroquinsoftware.labflowapi.model.Invoice;
import marroquinsoftware.labflowapi.model.InvoiceStatus;
import marroquinsoftware.labflowapi.model.JournalEntry;
import marroquinsoftware.labflowapi.model.JournalSourceType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Filtros opcionales de los listados de facturación y contabilidad.
 *
 * <p>Se arman con Criteria y no con JPQL a propósito. La forma corta de escribir
 * un filtro opcional —{@code where (:estado is null or x.estado = :estado)}—
 * revienta contra PostgreSQL cuando el filtro viene vacío: el parámetro solo
 * aparece en un {@code is null}, Postgres no puede deducir de qué tipo es y
 * responde "could not determine data type of parameter $N" (o lo toma por bytea,
 * y entonces falla el {@code lower()} de la búsqueda). H2, con el que corre el
 * resto de la suite, es mucho más permisivo y lo deja pasar, así que el error
 * solo aparecía en producción. Construyendo el where con las condiciones que de
 * verdad se piden, los parámetros vacíos ni llegan a la consulta.
 *
 * <p>El laboratorio no se filtra aquí: lo agrega Hibernate por {@code @TenantId}.
 */
public final class BillingSpecifications {

    private BillingSpecifications() {}

    public static Specification<Invoice> invoices(InvoiceStatus status, Long orderId,
                                                  Instant from, Instant to, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (orderId != null) predicates.add(cb.equal(root.get("order").get("id"), orderId));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("issuedAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("issuedAt"), to));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("invoiceNumber")), pattern),
                        cb.like(cb.lower(root.get("customerName")), pattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<JournalEntry> journalEntries(LocalDate from, LocalDate to,
                                                             JournalSourceType sourceType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("entryDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("entryDate"), to));
            if (sourceType != null) predicates.add(cb.equal(root.get("sourceType"), sourceType));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Expense> expenses(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
