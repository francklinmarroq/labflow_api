package marroquinsoftware.labflowapi.service;

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
import org.springframework.stereotype.Service;

/**
 * Siembra el catálogo por defecto de un laboratorio recién creado.
 *
 * <p>Se invoca desde {@link RegistrationService} con el {@code TenantContext} ya
 * apuntando al laboratorio nuevo, de modo que todas las entidades que se persistan
 * aquí reciben su {@code laboratory_id} automáticamente (vía {@code @TenantId}).
 * Por eso este seeder NO necesita setear el laboratorio en cada entidad: solo
 * guarda unidades, parámetros, exámenes, perfiles y rangos, y Hibernate los liga
 * al tenant en curso.
 *
 * <p>El contenido concreto del catálogo default se generará a partir de una base
 * de datos de referencia con catálogos ya configurados (pendiente de acceso). El
 * método {@link #seedDefaultCatalog()} es el punto donde se insertará esa data,
 * respetando el orden de dependencias: Unit → Parameter → AgeRange → ReferenceRange,
 * y Test → TestConfig → TestConfigParameter.
 */
@Service
public class CatalogSeeder {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogSeeder.class);

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private ParameterRepository parameterRepository;

    @Autowired
    private AgeRangeRepository ageRangeRepository;

    @Autowired
    private ReferenceRangeRepository referenceRangeRepository;

    @Autowired
    private PathologyRepository pathologyRepository;

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private TestConfigRepository testConfigRepository;

    /**
     * Inserta el catálogo por defecto en el laboratorio (tenant) actual.
     *
     * <p>TODO: poblar con el catálogo de referencia. Ejemplo del patrón a seguir
     * (Hibernate asigna el laboratory_id al persistir):
     * <pre>{@code
     *   Unit mgdl = unitRepository.save(new Unit(null, "mg/dL"));
     *   Parameter glucosa = new Parameter();
     *   glucosa.setName("Glucosa");
     *   glucosa.setUnit(mgdl);
     *   parameterRepository.save(glucosa);
     *   // ...Test -> TestConfig -> parámetros, ReferenceRange, etc.
     * }</pre>
     */
    public void seedDefaultCatalog() {
        // Pendiente: se llenará con la data de la BD de referencia.
        LOGGER.info("CatalogSeeder: catálogo por defecto pendiente de datos; no se sembró nada.");
    }
}
