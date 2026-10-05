package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

    // El laboratorio (tenant) lo filtra Hibernate por @TenantId en todas estas.
    // El listado con búsqueda y filtro de estado se arma con
    // PurchaseSpecifications.suppliers(), por la misma razón que BillingSpecifications.
    Optional<Supplier> findFirstByRtn(String rtn);
}
