package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.BillingClient;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * El laboratorio no va en ninguna firma: lo filtra Hibernate por el
 * {@code @TenantId} de la entidad, así que {@code findByRtn} ya solo ve los
 * clientes del laboratorio en contexto y el RTN es único dentro de ese ámbito.
 *
 * <p>El listado usa el {@code findAll(Pageable)} que hereda de JpaRepository: la
 * búsqueda por nombre o RTN la hace el frontend sobre lo que descarga, no la API.
 */
public interface BillingClientRepository extends JpaRepository<BillingClient, Long> {

    boolean existsByRtn(String rtn);

    /** Para nombrar en el mensaje al cliente que ya tiene ese RTN. */
    BillingClient findByRtn(String rtn);
}
