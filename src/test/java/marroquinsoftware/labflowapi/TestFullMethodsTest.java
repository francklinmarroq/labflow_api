package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import marroquinsoftware.labflowapi.config.AppConfig;
import marroquinsoftware.labflowapi.controller.v1.TestController;
import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.ParameterValueType;
import marroquinsoftware.labflowapi.model.TestConfig;
import marroquinsoftware.labflowapi.model.TestMethod;
import marroquinsoftware.labflowapi.payload.TestConfigDTO;
import marroquinsoftware.labflowapi.payload.TestFullDTO;
import marroquinsoftware.labflowapi.payload.TestFullParameterDTO;
import marroquinsoftware.labflowapi.payload.TestMethodDTO;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.TestConfigRepository;
import marroquinsoftware.labflowapi.repositories.TestMethodRepository;
import marroquinsoftware.labflowapi.service.ParameterServiceImp;
import marroquinsoftware.labflowapi.service.ReferenceRangeServiceImp;
import marroquinsoftware.labflowapi.service.TestBuilderService;
import marroquinsoftware.labflowapi.service.TestBuilderServiceImp;
import marroquinsoftware.labflowapi.service.TestConfigService;
import marroquinsoftware.labflowapi.service.TestConfigServiceImp;
import marroquinsoftware.labflowapi.service.TestMethodServiceImp;
import marroquinsoftware.labflowapi.service.TestServiceImp;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import marroquinsoftware.labflowapi.tenant.TenantIdentifierResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los métodos del perfil administrados con el examen, por el editor unificado
 * ({@code POST/PUT /api/v1/tests/full}): se guardan con el resto del agregado, el
 * mismo método escrito dos veces es uno solo, quitar uno que alguna orden indique
 * revierte el guardado entero, y los métodos vuelven con el perfil que lee la
 * pantalla de órdenes.
 *
 * <p>Se recorre el camino completo por el controlador —deserialización del JSON,
 * guardado, relectura y serialización— igual que {@link TestFullProfileSettingsTest}
 * y por el mismo motivo: lo que falla en estas cosas no suele ser el servicio suelto
 * sino el camino entero.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({TestController.class, TestBuilderServiceImp.class, TestServiceImp.class,
        TestConfigServiceImp.class, ParameterServiceImp.class, ReferenceRangeServiceImp.class,
        TestMethodServiceImp.class, TenantIdentifierResolver.class, AppConfig.class})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class TestFullMethodsTest {

    @Autowired TestController testController;
    @Autowired TestBuilderService testBuilderService;
    @Autowired TestConfigService testConfigService;
    @Autowired TestConfigRepository testConfigRepository;
    @Autowired TestMethodRepository testMethodRepository;
    @Autowired LabTestRepository labTestRepository;
    @Autowired LabOrderRepository labOrderRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired JsonMapper objectMapper;
    @PersistenceContext EntityManager entityManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Laboratory laboratory = new Laboratory();
        laboratory.setName("Laboratorio de Prueba");
        TenantContext.setLaboratoryId(laboratoryRepository.save(laboratory).getId());

        mockMvc = MockMvcBuilders.standaloneSetup(testController)
                .setMessageConverters(new JacksonJsonHttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    // --- Utilidades ---

    /** Examen mínimo válido: el examen, su perfil y un parámetro. */
    private TestFullDTO body(String name, TestMethodDTO... methods) {
        TestFullDTO dto = new TestFullDTO();
        dto.setName(name);
        dto.setProfileName(name);
        dto.setActive(true);
        TestFullParameterDTO parameter = new TestFullParameterDTO();
        parameter.setName("Resultado de " + name);
        parameter.setValueType(ParameterValueType.QUALITATIVE);
        dto.setParameters(new ArrayList<>(List.of(parameter)));
        dto.setMethods(new ArrayList<>(List.of(methods)));
        return dto;
    }

    private TestFullDTO createFull(TestFullDTO dto) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tests/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, TestFullDTO.class);
    }

    private TestFullDTO updateFull(Long testId, TestFullDTO dto) throws Exception {
        String response = mockMvc.perform(put("/api/v1/tests/{testId}/full", testId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, TestFullDTO.class);
    }

    /** Lee el examen después de vaciar el contexto, para que el GET toque la base. */
    private TestFullDTO reread(Long testId) throws Exception {
        entityManager.flush();
        entityManager.clear();
        String response = mockMvc.perform(get("/api/v1/tests/{testId}/full", testId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, TestFullDTO.class);
    }

    private TestMethodDTO method(String name, boolean isDefault) {
        return new TestMethodDTO(null, name, isDefault);
    }

    private String defaultNameOf(List<TestMethodDTO> methods) {
        return methods.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsDefault()))
                .map(TestMethodDTO::getName)
                .findFirst().orElse(null);
    }

    // --- Guardar los métodos con el examen ---

    @Test
    void savingAnExamWithThreeMethodsLeavesTheTemplateHoldingThree() throws Exception {
        TestFullDTO created = createFull(body("Perfil Tiroideo",
                method("ELISA", false),
                method("Quimioluminiscencia", true),
                method("Aglutinación", false)));

        assertEquals(3, created.getMethods().size());
        TestFullDTO stored = reread(created.getId());
        assertEquals(3, stored.getMethods().size(), "los tres quedan en el perfil");
        assertEquals("Quimioluminiscencia", defaultNameOf(stored.getMethods()));
    }

    // Nombrar el mismo método dos veces en un guardado no es un error: es el mismo
    // método nombrado otra vez, y el perfil queda con uno.
    @Test
    void twoSpellingsOfOneMethodInASingleSaveCollapseToOne() throws Exception {
        TestFullDTO created = createFull(body("VIH",
                method("ELISA", false),
                method("elisa", true)));

        TestFullDTO stored = reread(created.getId());
        assertEquals(1, stored.getMethods().size(), "uno, no dos, y sin rechazar el guardado");
        assertEquals("ELISA", stored.getMethods().get(0).getName(), "se conserva la primera escritura");
        assertEquals("ELISA", defaultNameOf(stored.getMethods()),
                "basta que una de las repetidas venga marcada para que el que queda sea el predeterminado");
    }

    @Test
    void methodsCanBeAddedRenamedAndRemovedFromTheEditor() throws Exception {
        TestFullDTO created = createFull(body("Toxoplasma", method("quimioluminicencia", true)));
        TestFullDTO stored = reread(created.getId());
        Long methodId = stored.getMethods().get(0).getId();

        // Se corrige la escritura y se agrega otra técnica.
        TestFullDTO edited = body("Toxoplasma");
        edited.setMethods(new ArrayList<>(List.of(
                new TestMethodDTO(methodId, "Quimioluminiscencia", true),
                method("ELISA", false))));
        edited.setParameters(stored.getParameters());
        updateFull(created.getId(), edited);

        stored = reread(created.getId());
        assertEquals(2, stored.getMethods().size());
        assertEquals("Quimioluminiscencia", defaultNameOf(stored.getMethods()), "renombrado, no duplicado");

        // Y se quita el que no se usa: como ninguna orden lo indica, se va sin más.
        TestFullDTO trimmed = body("Toxoplasma");
        trimmed.setMethods(new ArrayList<>(List.of(new TestMethodDTO(methodId, "Quimioluminiscencia", true))));
        trimmed.setParameters(stored.getParameters());
        updateFull(created.getId(), trimmed);

        assertEquals(1, reread(created.getId()).getMethods().size());
    }

    // El frontend desplegado ANTES de este cambio no manda el campo. Tiene que poder
    // seguir guardando exámenes, y sin llevarse por delante los métodos que el
    // backfill acaba de poner en los perfiles: el API se despliega primero.
    @Test
    void aSaveThatDoesNotMentionTheMethodsLeavesThemAlone() throws Exception {
        TestFullDTO created = createFull(body("VIH", method("ELISA", true)));
        TestFullDTO stored = reread(created.getId());

        String json = """
                {"name":"VIH","profileName":"VIH","active":true,
                 "parameters":[{"name":"Resultado de VIH","valueType":"QUALITATIVE"}]}""";
        mockMvc.perform(put("/api/v1/tests/{testId}/full", created.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        stored = reread(created.getId());
        assertEquals(1, stored.getMethods().size(), "omitir el campo no borra los métodos del perfil");
        assertEquals("ELISA", defaultNameOf(stored.getMethods()), "ni cambia el predeterminado");
    }

    // Mandar la lista vacía sí los quita: es el editor diciendo que el perfil se queda
    // sin métodos, no un cliente que no conoce el campo.
    @Test
    void anExplicitlyEmptyListRemovesTheMethods() throws Exception {
        TestFullDTO created = createFull(body("Dengue", method("ELISA", true)));
        TestFullDTO stored = reread(created.getId());

        TestFullDTO emptied = body("Dengue");
        emptied.setParameters(stored.getParameters());
        emptied.setMethods(new ArrayList<>());
        updateFull(created.getId(), emptied);

        assertTrue(reread(created.getId()).getMethods().isEmpty());
    }

    // Los métodos viajan con el perfil que la pantalla de órdenes ya cachea, así que
    // el selector no necesita una llamada aparte.
    @Test
    void methodsAndTheDefaultComeBackOnTheTestConfigDTO() throws Exception {
        TestFullDTO created = createFull(body("VIH",
                method("ELISA", false),
                method("Quimioluminiscencia", true)));
        entityManager.flush();
        entityManager.clear();

        List<TestConfigDTO> configs = testConfigService
                .getAllTestConfigs(0, 50, "id", "asc").getContent();

        TestConfigDTO config = configs.stream()
                .filter(c -> c.getId().equals(created.getTestConfigId())).findFirst().orElseThrow();
        assertEquals(2, config.getMethods().size());
        assertEquals("Quimioluminiscencia", defaultNameOf(config.getMethods()));
    }

    /**
     * Quitar un método que alguna orden indica revierte el guardado ENTERO del
     * examen, no solo los métodos: si no, quedaría un examen a medias (renombrado y
     * con los parámetros ya sincronizados) y los métodos como estaban.
     *
     * <p>Corre FUERA de la transacción de la prueba —a diferencia del resto de este
     * archivo— porque es lo único que hace observable la reversión: con la
     * transacción del test envolviendo al servicio, lo escrito antes del rechazo
     * sigue visible para esa misma transacción y la prueba no probaría nada. Los
     * datos que deja quedan bajo otro laboratorio y el filtro de tenant los ignora.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aSaveThatWouldDeleteAMethodInUseIsRefusedAndRollsBackTheRest() {
        TestFullDTO created = testBuilderService.createFull(body("Hepatitis B", method("ELISA", true)));
        TestConfig config = testConfigRepository.findById(created.getTestConfigId()).orElseThrow();
        // Por el repositorio y no por config.getMethods(): esta prueba corre fuera de
        // transacción a propósito, así que la colección perezosa no se puede tocar.
        TestMethod elisa = testMethodRepository
                .findByTestConfig_IdOrderByNameAsc(config.getId()).get(0);

        // Una orden que ya indica ese método: es lo que lo vuelve indeleble.
        Customer customer = new Customer();
        customer.setName("Paciente de Prueba");
        customer.setAgeInDays(30 * 365);
        customer = customerRepository.save(customer);
        LabOrder order = new LabOrder();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order = labOrderRepository.save(order);
        LabTest labTest = new LabTest();
        labTest.setOrder(order);
        labTest.setTest(config.getTest());
        labTest.setTestConfig(config);
        labTest.setMethod(elisa);
        labTestRepository.save(labTest);

        // El editor intenta quitar el método y, de paso, renombrar el examen y dejarlo
        // sin parámetros.
        TestFullDTO edited = body("Hepatitis B (superficie)");
        edited.setParameters(new ArrayList<>());
        edited.setMethods(new ArrayList<>());

        APIException error = assertThrows(APIException.class,
                () -> testBuilderService.updateFull(edited, created.getId()));
        assertTrue(error.getMessage().contains("ELISA"), error.getMessage());

        TestFullDTO after = testBuilderService.getFull(created.getId());
        assertEquals("Hepatitis B", after.getName(), "el examen no se renombró");
        assertEquals(1, after.getParameters().size(), "los parámetros del examen quedaron como estaban");
        assertEquals(1, after.getMethods().size(), "y el método sigue en el perfil");
    }
}
