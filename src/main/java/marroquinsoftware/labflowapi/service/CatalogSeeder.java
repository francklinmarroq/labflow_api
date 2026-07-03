package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.repositories.AgeRangeRepository;
import marroquinsoftware.labflowapi.repositories.ParameterRepository;
import marroquinsoftware.labflowapi.repositories.PathologyRepository;
import marroquinsoftware.labflowapi.repositories.ReferenceRangeRepository;
import marroquinsoftware.labflowapi.repositories.TestConfigRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import marroquinsoftware.labflowapi.repositories.UnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Siembra el catálogo por defecto de un laboratorio recién creado.
 *
 * <p>Se invoca desde {@link RegistrationService} con el {@code TenantContext} ya
 * apuntando al laboratorio nuevo, de modo que todas las entidades que se persistan
 * aquí reciben su {@code laboratory_id} automáticamente (vía {@code @TenantId}).
 *
 * <p>El catálogo default vive en el recurso {@code default-catalog.json} (generado
 * a partir de una base de datos de referencia). Se clona respetando las relaciones:
 * como los ids del JSON son los de la BD original, se lleva un mapa
 * {@code idOriginal -> entidad nueva} para reconstruir las llaves foráneas.
 */
@Service
public class CatalogSeeder {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogSeeder.class);
    private static final String CATALOG_RESOURCE = "default-catalog.json";

    @Autowired private UnitRepository unitRepository;
    @Autowired private ParameterRepository parameterRepository;
    @Autowired private AgeRangeRepository ageRangeRepository;
    @Autowired private ReferenceRangeRepository referenceRangeRepository;
    @Autowired private PathologyRepository pathologyRepository;
    @Autowired private TestRepository testRepository;
    @Autowired private TestConfigRepository testConfigRepository;
    @Autowired private ObjectMapper objectMapper;

    /**
     * Inserta el catálogo por defecto en el laboratorio (tenant) actual. Idempotente:
     * si el laboratorio ya tiene catálogo, no hace nada.
     */
    public void seedDefaultCatalog() {
        if (unitRepository.count() > 0 || testRepository.count() > 0) {
            LOGGER.info("CatalogSeeder: el laboratorio ya tiene catálogo; no se sembró nada.");
            return;
        }

        Catalog catalog = readCatalog();

        Map<Long, Unit> units = new HashMap<>();
        for (UnitRow r : catalog.units()) {
            Unit u = new Unit();
            u.setUnitSymbol(r.unit_symbol());
            units.put(r.id(), unitRepository.save(u));
        }

        Map<Long, AgeRange> ageRanges = new HashMap<>();
        for (AgeRangeRow r : catalog.ageRanges()) {
            AgeRange a = new AgeRange();
            a.setName(r.name());
            a.setMinAgeDays(r.min_age_days());
            a.setMaxAgeDays(r.max_age_days());
            ageRanges.put(r.id(), ageRangeRepository.save(a));
        }

        for (PathologyRow r : catalog.pathologies()) {
            Pathology p = new Pathology();
            p.setName(r.name());
            pathologyRepository.save(p);
        }

        Map<Long, Parameter> parameters = new HashMap<>();
        for (ParameterRow r : catalog.parameters()) {
            Parameter p = new Parameter();
            p.setName(r.name());
            p.setSection(parseEnum(ParameterSection.class, r.section()));
            p.setValueType(parseEnum(ParameterValueType.class, r.value_type()));
            if (r.unit_id() != null) p.setUnit(units.get(r.unit_id()));
            parameters.put(r.id(), parameterRepository.save(p));
        }

        for (ReferenceRangeRow r : catalog.referenceRanges()) {
            ReferenceRange rr = new ReferenceRange();
            rr.setParameter(parameters.get(r.parameter_id()));
            rr.setSex(parseEnum(Sex.class, r.sex()));
            if (r.age_range_id() != null) rr.setAgeRange(ageRanges.get(r.age_range_id()));
            rr.setLowerLimit(r.lower_limit());
            rr.setLowerExclusive(Boolean.TRUE.equals(r.lower_exclusive()));
            rr.setUpperLimit(r.upper_limit());
            rr.setUpperExclusive(Boolean.TRUE.equals(r.upper_exclusive()));
            rr.setCriticalLow(r.critical_low());
            rr.setCriticalHigh(r.critical_high());
            rr.setInterpretationText(r.interpretation_text());
            referenceRangeRepository.save(rr);
        }

        Map<Long, Test> tests = new HashMap<>();
        for (TestRow r : catalog.tests()) {
            Test t = new Test();
            t.setName(r.name());
            t.setPrice(r.price());
            t.setCost(r.cost());
            t.setArea(parseEnum(TestArea.class, r.area()));
            tests.put(r.id(), testRepository.save(t));
        }

        // Agrupa los parámetros de cada perfil por su test_config_id original.
        Map<Long, List<TestConfigParameterRow>> paramsByConfig = new HashMap<>();
        for (TestConfigParameterRow r : catalog.testConfigParameters()) {
            paramsByConfig.computeIfAbsent(r.test_config_id(), k -> new ArrayList<>()).add(r);
        }

        for (TestConfigRow r : catalog.testConfigs()) {
            TestConfig tc = new TestConfig();
            tc.setTest(tests.get(r.test_id()));
            tc.setName(r.name());
            tc.setActive(Boolean.TRUE.equals(r.active()));
            ChartType chartType = parseEnum(ChartType.class, r.chart_type());
            if (chartType != null) tc.setChartType(chartType);
            tc.setChartXAxisLabel(r.chart_x_axis_label());
            for (TestConfigParameterRow p : paramsByConfig.getOrDefault(r.id(), List.of())) {
                tc.getConfigParameters().add(new TestConfigParameter(
                        tc, parameters.get(p.parameter_id()), p.display_order(), p.chart_x_value()));
            }
            testConfigRepository.save(tc); // cascada persiste los TestConfigParameter
        }

        LOGGER.info("CatalogSeeder: sembradas {} unidades, {} parámetros, {} exámenes, {} perfiles.",
                units.size(), parameters.size(), tests.size(), catalog.testConfigs().size());
    }

    private Catalog readCatalog() {
        try (InputStream in = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            return objectMapper.readValue(in, Catalog.class);
        } catch (IOException e) {
            throw new APIException("No se pudo leer " + CATALOG_RESOURCE + ": " + e.getMessage());
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    // Estructura del recurso default-catalog.json. Los nombres de campo coinciden
    // exactamente con las claves del JSON (snake_case en las filas) para que Jackson
    // los enlace sin estrategia de nombres.
    private record Catalog(
            List<UnitRow> units,
            List<AgeRangeRow> ageRanges,
            List<PathologyRow> pathologies,
            List<ParameterRow> parameters,
            List<ReferenceRangeRow> referenceRanges,
            List<TestRow> tests,
            List<TestConfigRow> testConfigs,
            List<TestConfigParameterRow> testConfigParameters) {}

    private record UnitRow(Long id, String unit_symbol) {}

    private record AgeRangeRow(Long id, String name, Integer min_age_days, Integer max_age_days) {}

    private record PathologyRow(Long id, String name) {}

    private record ParameterRow(Long id, String name, String section, String value_type, Long unit_id) {}

    private record ReferenceRangeRow(
            Long id, Long parameter_id, String sex, Long age_range_id,
            BigDecimal lower_limit, Boolean lower_exclusive,
            BigDecimal upper_limit, Boolean upper_exclusive,
            BigDecimal critical_low, BigDecimal critical_high, String interpretation_text) {}

    private record TestRow(Long id, String name, BigDecimal price, BigDecimal cost, String area) {}

    private record TestConfigRow(
            Long id, Long test_id, String name, Boolean active,
            String chart_type, String chart_x_axis_label) {}

    private record TestConfigParameterRow(
            Long test_config_id, Long parameter_id, Integer display_order, BigDecimal chart_x_value) {}
}
