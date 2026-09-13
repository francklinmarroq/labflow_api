package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.criteria.JoinType;
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

    /**
     * @param tagId etiqueta de la ORDEN de la que salió la factura (convenio,
     *              campaña…); null = todas. Las etiquetas viven en la orden y no se
     *              copian a la factura: no son un dato fiscal, son una clasificación
     *              del laboratorio, y así renombrar una etiqueta se refleja en todo
     *              lo ya facturado sin tocar el documento congelado.
     * @param billingClientId cliente de facturación (empresa, aseguradora) al que se
     *              emitió la factura; null = todas, las emitidas a pacientes incluidas.
     */
    public static Specification<Invoice> invoices(InvoiceStatus status, Long orderId,
                                                  Instant from, Instant to, String search, Long tagId,
                                                  Long billingClientId) {
        return (root, query, cb) -> {
            // El mapeo a DTO de cada factura del listado lee order.id/order.orderNumber
            // y customer.id. Al ser @ManyToOne EAGER sin join, recorrer la página
            // dispararía una consulta por factura para el pedido y otra para el cliente
            // (N+1). Se traen ambos en la MISMA consulta de la página con un fetch join;
            // son to-one (no multiplican filas), así que la paginación por SQL sigue
            // siendo correcta y no aplica la paginación en memoria de los fetch de
            // colección. Se omite en la consulta de conteo (getResultType == Long), que
            // no navega esas relaciones.
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("order", JoinType.LEFT);
                root.fetch("customer", JoinType.LEFT);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (orderId != null) predicates.add(cb.equal(root.get("order").get("id"), orderId));
            // Igual que orderId: una comparación sobre la llave foránea que la fila
            // ya lleva, sin join, así que no duplica filas ni cambia el conteo. Y se
            // aplica en la consulta, no sobre la página: filtra todas las facturas.
            if (billingClientId != null) {
                predicates.add(cb.equal(root.get("billingClient").get("id"), billingClientId));
            }
            // `to` ya es el inicio del día siguiente (exclusivo), así que va con
            // menor-estricto para no arrastrar la medianoche del día siguiente.
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("issuedAt"), from));
            if (to != null) predicates.add(cb.lessThan(root.get("issuedAt"), to));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("invoiceNumber")), pattern),
                        cb.like(cb.lower(root.get("customerName")), pattern)));
            }
            if (tagId != null) {
                // La etiqueta cuelga de la orden, así que hay que llegar hasta ella.
                // Se agrega un join propio (no se reutiliza el fetch de arriba) para
                // que el filtro también valga en la consulta de conteo, que no lleva
                // fetch. Como se filtra por UNA etiqueta, el join no puede duplicar
                // filas —una orden no tiene dos veces la misma— y la paginación
                // sigue siendo correcta.
                predicates.add(cb.equal(
                        root.join("order", JoinType.INNER).join("tags", JoinType.INNER).get("id"),
                        tagId));
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
