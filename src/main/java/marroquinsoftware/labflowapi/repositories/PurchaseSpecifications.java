package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import marroquinsoftware.labflowapi.model.Purchase;
import marroquinsoftware.labflowapi.model.PurchaseStatus;
import marroquinsoftware.labflowapi.model.SaleCondition;
import marroquinsoftware.labflowapi.model.Supplier;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Filtros opcionales de los listados de compras y proveedores. Con Criteria y no
 * con JPQL por la misma razón que {@link BillingSpecifications}: un filtro vacío
 * escrito como {@code (:x is null or ...)} revienta en PostgreSQL.
 *
 * <p>El laboratorio no se filtra aquí: lo agrega Hibernate por {@code @TenantId}.
 */
public final class PurchaseSpecifications {

    private PurchaseSpecifications() {}

    /**
     * @param search texto que se busca en el nombre o el RTN; null = todos
     * @param active true/false filtra por estado; null = activos e inactivos
     */
    public static Specification<Supplier> suppliers(String search, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("rtn"), "")), like)));
            }
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Purchase> purchases(LocalDate from, LocalDate to, Long supplierId,
                                                    SaleCondition condition, PurchaseStatus status) {
        return (root, query, cb) -> {
            // El listado muestra el proveedor de cada compra: fetch join para no
            // hacer una consulta por fila. Es to-one y obligatorio, así que no
            // multiplica filas; se omite en la consulta de conteo.
            boolean isCount = query != null
                    && (query.getResultType() == Long.class || query.getResultType() == long.class);
            if (!isCount) root.fetch("supplier", JoinType.INNER);

            List<Predicate> predicates = new ArrayList<>();
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("purchaseDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("purchaseDate"), to));
            if (supplierId != null) predicates.add(cb.equal(root.get("supplier").get("id"), supplierId));
            if (condition != null) predicates.add(cb.equal(root.get("condition"), condition));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
