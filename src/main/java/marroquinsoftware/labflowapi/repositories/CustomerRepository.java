package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Customer findByNationalIdNumber(String nationalIdNumber);
    Customer findByTaxNumber(String taxNumber);
}
