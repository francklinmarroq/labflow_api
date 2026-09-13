package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import marroquinsoftware.labflowapi.config.AppConfig;
import marroquinsoftware.labflowapi.controller.v1.TestController;
import marroquinsoftware.labflowapi.model.ChartType;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.ParameterValueType;
import marroquinsoftware.labflowapi.model.ResultLayout;
import marroquinsoftware.labflowapi.model.TestArea;
import marroquinsoftware.labflowapi.payload.TestFullDTO;
import marroquinsoftware.labflowapi.payload.TestFullParameterDTO;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.service.ParameterServiceImp;
import marroquinsoftware.labflowapi.service.ReferenceRangeServiceImp;
import marroquinsoftware.labflowapi.service.TestBuilderServiceImp;
import marroquinsoftware.labflowapi.service.TestConfigServiceImp;
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

import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ajustes del perfil en el editor unificado de examen: lo que el cliente guarda es
 * lo que una lectura posterior devuelve. El interruptor de "permitir adjuntar foto
 * del reporte" es el que motivó estas pruebas —se encendía, se guardaba, y al
 * reabrir el editor aparecía apagado— pero la regresión se cuida para los cinco
 * ajustes del perfil, porque el modo de falla (reportar como guardado un valor que
 * el siguiente GET contradice) es el mismo para todos.
 *
 * <p>Se recorre el camino completo por el controlador —deserialización del JSON,
 * guardado, relectura y serialización— y no el servicio suelto: el servicio siempre
 * asignó bien el campo; lo que fallaba era el camino entero. Entre el guardado y la
 * lectura se vacía el contexto de persistencia para que el GET vaya de verdad a la
 * base y no lea de la caché de la sesión.
 *
 * <p>Límite honesto: acá el esquema lo construye {@code ddl-auto} desde las
 * entidades, así que en H2 la columna siempre existe. Estas pruebas cuidan el camino
 * Java; NO habrían detectado la caída de producción, que fue una columna ausente en
 * la base. Eso se verifica contra producción (ver tasks.md de este cambio).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({TestController.class, TestBuilderServiceImp.class, TestServiceImp.class,
        TestConfigServiceImp.class, ParameterServiceImp.class, ReferenceRangeServiceImp.class,
        TenantIdentifierResolver.class, AppConfig.class})
// El ObjectMapper de la aplicación, no uno crudo: Spring Boot le apaga
// FAIL_ON_NULL_FOR_PRIMITIVES, que Jackson 3 trae encendido. Con un mapper crudo,
// un cuerpo que omite un boolean primitivo (active, allowResultAttachments) se
// rechaza con 400 — algo que la aplicación real no hace. Sin esto la prueba de
// "cliente que no manda el campo" mediría el arnés, no la API.
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class TestFullProfileSettingsTest {

    @Autowired TestController testController;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired JsonMapper objectMapper;
    @PersistenceContext EntityManager entityManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Laboratory laboratory = new Laboratory();
        laboratory.setName("Laboratorio de Prueba");
        TenantContext.setLaboratoryId(laboratoryRepository.save(laboratory).getId());

        // standaloneSetup levanta solo el controlador sobre el stack de Spring MVC:
        // alcanza para ejercitar el binding de JSON sin arrastrar el contexto
        // completo de la aplicación (que necesita DB_URL del .env).
        mockMvc = MockMvcBuilders.standaloneSetup(testController)
                .setMessageConverters(new JacksonJsonHttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    // --- Helpers ---------------------------------------------------------------

    /** Examen mínimo válido: el examen, su perfil y un parámetro. */
    private TestFullDTO body(String name) {
        TestFullDTO dto = new TestFullDTO();
        dto.setName(name);
        dto.setProfileName(name);
        dto.setActive(true);
        TestFullParameterDTO parameter = new TestFullParameterDTO();
        parameter.setName("Glucosa");
        parameter.setValueType(ParameterValueType.QUANTITATIVE);
        dto.setParameters(List.of(parameter));
        return dto;
    }

    /** Reenvía como cuerpo de un PUT lo que devolvió el servidor, igual que la UI. */
    private TestFullDTO resend(TestFullDTO previous) {
        TestFullDTO dto = new TestFullDTO();
        dto.setName(previous.getName());
        dto.setProfileName(previous.getProfileName());
        dto.setActive(previous.isActive());
        dto.setArea(previous.getArea());
        dto.setChartType(previous.getChartType());
        dto.setChartXAxisLabel(previous.getChartXAxisLabel());
        dto.setResultLayout(previous.getResultLayout());
        dto.setAllowResultAttachments(previous.isAllowResultAttachments());
        dto.setParameters(previous.getParameters());
        return dto;
    }

    private TestFullDTO createFull(TestFullDTO dto) throws Exception {
        return createFull(objectMapper.writeValueAsString(dto));
    }

    private TestFullDTO createFull(String json) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tests/full")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
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

    // --- El interruptor de adjuntos --------------------------------------------

    // El síntoma original: se enciende, se guarda, se sale del editor, se vuelve a
    // entrar y aparece apagado.
    @Test
    void turningAttachmentsOnForAnExistingExamPersists() throws Exception {
        TestFullDTO created = createFull(body("Hemograma"));
        assertFalse(created.isAllowResultAttachments(), "el examen nace con los adjuntos apagados");

        TestFullDTO edited = resend(created);
        edited.setAllowResultAttachments(true);

        assertTrue(updateFull(created.getId(), edited).isAllowResultAttachments(),
                "la respuesta del guardado reporta el interruptor encendido");
        assertTrue(reread(created.getId()).isAllowResultAttachments(),
                "al reabrir el editor sigue encendido");
    }

    // Apagarlo también tiene que quedar: si no, el valor no se está guardando, solo
    // se está devolviendo el defecto.
    @Test
    void turningAttachmentsBackOffPersists() throws Exception {
        TestFullDTO created = createFull(body("Hemograma"));
        TestFullDTO on = resend(created);
        on.setAllowResultAttachments(true);
        updateFull(created.getId(), on);

        TestFullDTO off = resend(reread(created.getId()));
        off.setAllowResultAttachments(false);

        assertFalse(updateFull(created.getId(), off).isAllowResultAttachments());
        assertFalse(reread(created.getId()).isAllowResultAttachments(),
                "apagarlo se conserva; no vuelve a encenderse solo");
    }

    @Test
    void creatingAnExamWithAttachmentsAllowed() throws Exception {
        TestFullDTO dto = body("Urocultivo");
        dto.setAllowResultAttachments(true);

        TestFullDTO created = createFull(dto);

        assertTrue(created.isAllowResultAttachments());
        assertTrue(reread(created.getId()).isAllowResultAttachments());
    }

    // Un cliente que todavía no conoce el campo no lo manda. El examen tiene que
    // nacer con los adjuntos apagados, no encenderse por omisión.
    @Test
    void anExamCreatedWithoutMentioningTheSettingDefaultsToOff() throws Exception {
        String json = """
                {"name":"General de Orina","profileName":"General de Orina","active":true,
                 "parameters":[{"name":"Color","valueType":"QUALITATIVE"}]}""";

        TestFullDTO created = createFull(json);

        assertFalse(created.isAllowResultAttachments());
        assertFalse(reread(created.getId()).isAllowResultAttachments(),
                "omitir el campo lo deja apagado");
    }

    // Los dos ajustes son independientes: un antibiograma puede además llevar la
    // foto que imprime el equipo, y cambiar uno no puede arrastrar al otro.
    @Test
    void theSwitchIsIndependentOfTheAntibiogramLayout() throws Exception {
        TestFullDTO dto = body("Antibiograma");
        dto.setResultLayout(ResultLayout.ANTIBIOGRAM);
        dto.setAllowResultAttachments(true);

        TestFullDTO created = createFull(dto);
        TestFullDTO stored = reread(created.getId());
        assertEquals(ResultLayout.ANTIBIOGRAM, stored.getResultLayout());
        assertTrue(stored.isAllowResultAttachments());

        // Se apagan los adjuntos: la disposición del reporte queda como estaba.
        TestFullDTO withoutAttachments = resend(stored);
        withoutAttachments.setAllowResultAttachments(false);
        updateFull(created.getId(), withoutAttachments);

        stored = reread(created.getId());
        assertFalse(stored.isAllowResultAttachments());
        assertEquals(ResultLayout.ANTIBIOGRAM, stored.getResultLayout(),
                "apagar los adjuntos no devuelve el perfil a la tabla estándar");

        // Y al revés: con los adjuntos encendidos, se vuelve a la tabla estándar.
        TestFullDTO backOn = resend(stored);
        backOn.setAllowResultAttachments(true);
        updateFull(created.getId(), backOn);

        TestFullDTO standard = resend(reread(created.getId()));
        standard.setResultLayout(ResultLayout.STANDARD);
        updateFull(created.getId(), standard);

        stored = reread(created.getId());
        assertEquals(ResultLayout.STANDARD, stored.getResultLayout());
        assertTrue(stored.isAllowResultAttachments(),
                "volver a la tabla estándar no apaga los adjuntos");
    }

    // Ningún ajuste se guarda a medias: los cinco viajan en el mismo payload y los
    // cinco tienen que volver iguales.
    @Test
    void everyProfileSettingSurvivesOneRoundTrip() throws Exception {
        TestFullDTO dto = body("Curva de Tolerancia");
        dto.setArea(TestArea.QUIMICA);
        dto.setActive(false);
        dto.setChartType(ChartType.LINE);
        dto.setChartXAxisLabel("Tiempo (min)");
        dto.setResultLayout(ResultLayout.ANTIBIOGRAM);
        dto.setAllowResultAttachments(true);

        TestFullDTO stored = reread(createFull(dto).getId());

        assertFalse(stored.isActive(), "active");
        assertEquals(ChartType.LINE, stored.getChartType(), "chartType");
        assertEquals("Tiempo (min)", stored.getChartXAxisLabel(), "chartXAxisLabel");
        assertEquals(ResultLayout.ANTIBIOGRAM, stored.getResultLayout(), "resultLayout");
        assertTrue(stored.isAllowResultAttachments(), "allowResultAttachments");
    }
}
