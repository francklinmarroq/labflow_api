package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import marroquinsoftware.labflowapi.model.Account;
import marroquinsoftware.labflowapi.model.SystemAccountKey;
import marroquinsoftware.labflowapi.repositories.AccountRepository;
import marroquinsoftware.labflowapi.service.AccountSeeder;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import marroquinsoftware.labflowapi.tenant.TenantIdentifierResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que el catálogo de cuentas por defecto (default-accounts.json) se
 * siembre completo: todas las cuentas del sistema presentes, sin duplicados,
 * idempotente y aislado por laboratorio.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({AccountSeeder.class, TenantIdentifierResolver.class, AccountSeedIntegrityTest.JacksonForTest.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class AccountSeedIntegrityTest {

    static class JacksonForTest {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
    }

    @Autowired AccountSeeder accountSeeder;
    @Autowired AccountRepository accountRepository;
    @Autowired EntityManager em;

    // El tenant se fija antes de que arranque la transacción del test: la sesión
    // de Hibernate resuelve su tenant al abrirse (al inicio de la transacción),
    // así que fijarlo dentro del método llegaría tarde.
    @BeforeAll
    static void setTenant() { TenantContext.setLaboratoryId(7L); }

    @AfterAll
    static void clearTenant() { TenantContext.clear(); }

    @Test
    void seedsFullChartOfAccounts() {
        accountSeeder.seedDefaultAccounts();

        List<Account> accounts = accountRepository.findAll();
        assertEquals(15, accounts.size(), "el catálogo por defecto debe tener 15 cuentas");

        // Todas las cuentas del sistema presentes, sin repetir clave.
        Set<SystemAccountKey> seededKeys = accounts.stream()
                .map(Account::getSystemKey)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        assertEquals(EnumSet.allOf(SystemAccountKey.class), seededKeys,
                "faltan cuentas del sistema en la semilla");

        // Sin códigos duplicados y todas activas.
        long distinctCodes = accounts.stream().map(Account::getCode).distinct().count();
        assertEquals(accounts.size(), distinctCodes, "hay códigos de cuenta duplicados");
        assertTrue(accounts.stream().allMatch(Account::isActive), "todas deben sembrarse activas");
    }

    @Test
    void seedingIsIdempotent() {
        accountSeeder.seedDefaultAccounts();
        long afterFirst = accountRepository.count();

        accountSeeder.seedDefaultAccounts();
        assertEquals(afterFirst, accountRepository.count(), "el seeder no es idempotente");
    }

    // El aislamiento entre laboratorios se verifica contra la columna física.
    @Test
    void seededAccountsBelongToTheCurrentLaboratory() {
        accountSeeder.seedDefaultAccounts();
        em.flush();

        Long wrongTenant = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM accounts WHERE laboratory_id IS NULL OR laboratory_id <> 7")
                .getSingleResult()).longValue();
        assertEquals(0L, wrongTenant, "todas las cuentas deben quedar con el laboratory_id del tenant que sembró");
    }
}
