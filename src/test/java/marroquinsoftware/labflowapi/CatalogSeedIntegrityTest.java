package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import marroquinsoftware.labflowapi.model.ReferenceContextKind;
import marroquinsoftware.labflowapi.repositories.*;
import marroquinsoftware.labflowapi.service.CatalogSeeder;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import marroquinsoftware.labflowapi.tenant.TenantIdentifierResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que el catálogo por defecto (default-catalog.json) se siembre completo
 * y consistente contra una BD real (H2). Es la red que faltaba: detecta un JSON
 * mal formado, un enum fuera de rango, un examen sin perfil o la pérdida de los
 * rangos contextuales/por edad al sembrar.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({CatalogSeeder.class, TenantIdentifierResolver.class, CatalogSeedIntegrityTest.JacksonForTest.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class CatalogSeedIntegrityTest {

    static class JacksonForTest {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
    }

    @Autowired CatalogSeeder catalogSeeder;
    @Autowired UnitRepository unitRepository;
    @Autowired ParameterRepository parameterRepository;
    @Autowired ReferenceRangeRepository referenceRangeRepository;
    @Autowired TestRepository testRepository;
    @Autowired TestConfigRepository testConfigRepository;
    @Autowired EntityManager em;

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void seedsFullCatalogWithoutOrphansAndKeepsContextRanges() {
        TenantContext.setLaboratoryId(1L);
        catalogSeeder.seedDefaultCatalog();
        em.flush();
        em.clear();

        // 1) Se sembró algo de cada tipo.
        assertTrue(unitRepository.count() > 0, "unidades");
        assertTrue(parameterRepository.count() > 0, "parámetros");
        assertTrue(referenceRangeRepository.count() > 0, "rangos");
        assertTrue(testRepository.count() > 0, "exámenes");
        assertTrue(testConfigRepository.count() > 0, "perfiles");

        // 2) Ningún examen queda sin perfil (todos reportan).
        Long orphanTests = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM tests t LEFT JOIN test_config tc ON tc.test_id = t.id WHERE tc.id IS NULL")
                .getSingleResult()).longValue();
        assertEquals(0L, orphanTests, "hay exámenes sin perfil");

        // 3) Ningún parámetro con value_type nulo (rompe el render del resultado).
        Long nullValueType = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM parameter WHERE value_type IS NULL").getSingleResult()).longValue();
        assertEquals(0L, nullValueType, "parámetros con value_type nulo");

        // 4) Los rangos contextuales (fase de ciclo/gestación/menopausia) se sembraron:
        //    esto falla si el seeder deja de mapear los campos de contexto.
        long contextRanges = referenceRangeRepository.findAll().stream()
                .filter(r -> r.getContextKind() != null && r.getContextKind() != ReferenceContextKind.NONE)
                .count();
        assertTrue(contextRanges > 0, "no se sembraron rangos contextuales");

        // 5) Idempotencia: correr de nuevo no duplica.
        long unitsAfterFirst = unitRepository.count();
        catalogSeeder.seedDefaultCatalog();
        assertEquals(unitsAfterFirst, unitRepository.count(), "el seeder no es idempotente");
    }
}
