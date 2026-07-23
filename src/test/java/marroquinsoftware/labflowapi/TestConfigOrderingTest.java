package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import marroquinsoftware.labflowapi.model.Parameter;
import marroquinsoftware.labflowapi.model.TestConfig;
import marroquinsoftware.labflowapi.model.TestConfigParameter;
import marroquinsoftware.labflowapi.repositories.ParameterRepository;
import marroquinsoftware.labflowapi.repositories.TestConfigRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import marroquinsoftware.labflowapi.tenant.TenantIdentifierResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
// Las entidades llevan @TenantId, así que Hibernate arranca en modo multi-tenant
// y exige un resolver para poder abrir sesión; sin este import el contexto ni
// siquiera levanta.
@Import(TenantIdentifierResolver.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class TestConfigOrderingTest {

    @Autowired
    TestConfigRepository testConfigRepository;
    @Autowired
    ParameterRepository parameterRepository;
    @Autowired
    TestRepository testRepository;
    @Autowired
    EntityManager em;

    private marroquinsoftware.labflowapi.model.Test newTest(String name) {
        marroquinsoftware.labflowapi.model.Test t = new marroquinsoftware.labflowapi.model.Test();
        t.setName(name);
        return testRepository.save(t);
    }

    private Parameter newParam(String name) {
        Parameter p = new Parameter();
        p.setName(name);
        return parameterRepository.save(p);
    }

    @Test
    void preservesOrderOnFreshSaveAndLoad() {
        var t = newTest("Hemograma");
        Parameter a = newParam("A");
        Parameter b = newParam("B");
        Parameter c = newParam("C");

        TestConfig cfg = new TestConfig();
        cfg.setTest(t);
        cfg.setName("Perfil-fresh");
        cfg.setActive(true);
        // orden deliberado: C, A, B
        cfg.getConfigParameters().add(new TestConfigParameter(cfg, c, 0));
        cfg.getConfigParameters().add(new TestConfigParameter(cfg, a, 1));
        cfg.getConfigParameters().add(new TestConfigParameter(cfg, b, 2));
        Long id = testConfigRepository.save(cfg).getId();

        em.flush();
        em.clear();

        TestConfig loaded = testConfigRepository.findById(id).orElseThrow();
        List<Long> ids = loaded.getConfigParameters().stream()
                .map(cp -> cp.getParameter().getId()).toList();
        System.out.println(">>> FRESH loaded order = " + ids
                + " expected = " + List.of(c.getId(), a.getId(), b.getId()));
        Assertions.assertEquals(List.of(c.getId(), a.getId(), b.getId()), ids);
    }

    @Test
    void loadingLegacyRowsWithNullDisplayOrderDoesNotFail() {
        var t = newTest("Quimica");
        Parameter a = newParam("X");
        Parameter b = newParam("Y");

        TestConfig cfg = new TestConfig();
        cfg.setTest(t);
        cfg.setName("Perfil-legacy");
        cfg.setActive(true);
        Long id = testConfigRepository.save(cfg).getId();
        em.flush();

        // Simula los datos previos a la migración: filas de unión SIN display_order.
        em.createNativeQuery(
                "INSERT INTO test_config_parameters (test_config_id, parameter_id) VALUES (:c, :p)")
                .setParameter("c", id).setParameter("p", a.getId()).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO test_config_parameters (test_config_id, parameter_id) VALUES (:c, :p)")
                .setParameter("c", id).setParameter("p", b.getId()).executeUpdate();
        em.flush();
        em.clear();

        // Antes (con @OrderColumn) esto lanzaba excepción y causaba el 500.
        // Con @OrderBy y columna nullable, debe leerse sin error.
        TestConfig loaded = testConfigRepository.findById(id).orElseThrow();
        List<Long> ids = loaded.getConfigParameters().stream()
                .map(cp -> cp.getParameter().getId()).toList();
        System.out.println(">>> LEGACY loaded order = " + ids);
        Assertions.assertEquals(2, ids.size());
    }
}
