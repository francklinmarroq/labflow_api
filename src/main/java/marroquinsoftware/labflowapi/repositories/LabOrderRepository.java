package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.LabOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// El listado paginado (con sus filtros de estado y etiqueta) se arma con
// LabOrderSpecifications, de ahí el JpaSpecificationExecutor.
public interface LabOrderRepository extends JpaRepository<LabOrder, Long>, JpaSpecificationExecutor<LabOrder> {
    List<LabOrder> findByCustomer_Id(Long customerId);

    // Orden por token del enlace público. El @TenantId sigue filtrando: se usa una
    // vez que el TenantContext ya quedó fijado con el laboratorio del token.
    Optional<LabOrder> findByPublicToken(String publicToken);

    // Solo el id del laboratorio dueño del token, con SQL nativo para saltarse el
    // filtro de @TenantId (el endpoint público llega sin tenant). Se usa para fijar
    // el TenantContext antes de abrir la sesión de la petición, igual que con las
    // invitaciones.
    @Query(value = "select laboratory_id from lab_orders where public_token = :token", nativeQuery = true)
    Optional<Long> findLaboratoryIdByPublicToken(@Param("token") String token);
}
