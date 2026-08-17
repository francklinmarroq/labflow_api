package marroquinsoftware.labflowapi.repositories;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtros del listado de órdenes. Se arman con Criteria y no con JPQL por el mismo
 * motivo que {@link BillingSpecifications}: la forma corta del filtro opcional
 * ({@code where (:param is null or ...)}) revienta contra PostgreSQL cuando el
 * parámetro viene vacío y pasa sin problema en H2, así que el error solo aparecía
 * en producción.
 *
 * <p>El laboratorio no se filtra aquí: lo agrega Hibernate por {@code @TenantId}.
 */
public final class LabOrderSpecifications {

    private LabOrderSpecifications() {}

    /**
     * @param status   estado exacto a listar; null lista las activas (todas menos
     *                 las canceladas, que viven en su propia pestaña).
     * @param tagId    etiqueta por la que filtrar (convenio, campaña…); null = todas.
     */
    public static Specification<LabOrder> orders(OrderStatus status, Long tagId) {
        return (root, query, cb) -> {
            // El mapeo a DTO de cada orden lee customer.name/sex/ageInDays. Al ser
            // @ManyToOne sin join, recorrer la página dispararía una consulta por orden
            // (N+1); se trae en la MISMA consulta con un fetch join. Es to-one, así que
            // no multiplica filas y la paginación por SQL sigue siendo correcta. Se
            // omite en la consulta de conteo, que no navega la relación.
            // Las etiquetas NO se traen con fetch: son una colección y el fetch join
            // obligaría a Hibernate a paginar en memoria. Se resuelven por lotes con
            // el @BatchSize de LabOrder.tags.
            boolean isCount = query != null
                    && (query.getResultType() == Long.class || query.getResultType() == long.class);
            if (!isCount) {
                root.fetch("customer", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                // Sin filtro se listan las activas: la cancelación es un borrado lógico.
                predicates.add(cb.notEqual(root.get("status"), OrderStatus.CANCELLED));
            }
            if (tagId != null) {
                // Join a la tabla de unión. Como se filtra por UNA etiqueta, el join no
                // duplica filas (una orden no puede tener la misma etiqueta dos veces),
                // así que el conteo y la paginación siguen siendo correctos.
                predicates.add(cb.equal(root.join("tags", JoinType.INNER).get("id"), tagId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
