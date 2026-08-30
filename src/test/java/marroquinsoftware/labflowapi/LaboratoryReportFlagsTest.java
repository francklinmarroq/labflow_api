package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import marroquinsoftware.labflowapi.config.AppConfig;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.payload.LaboratoryDTO;
import marroquinsoftware.labflowapi.payload.PublicReportDTO;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.service.AccountSeeder;
import marroquinsoftware.labflowapi.service.CatalogSeeder;
import marroquinsoftware.labflowapi.service.FileStorageService;
import marroquinsoftware.labflowapi.service.LaboratoryService;
import marroquinsoftware.labflowapi.service.LaboratoryServiceImp;
import marroquinsoftware.labflowapi.service.PublicReportServiceImp;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import marroquinsoftware.labflowapi.tenant.TenantIdentifierResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Interruptor de las alertas del reporte: cada laboratorio decide si su reporte
 * marca los valores fuera de rango — (Alto), (Bajo), (¡Crítico!) y el resaltado
 * en negrita. Está encendido mientras nadie lo apague, apagarlo se conserva, y
 * el reporte público del paciente respeta la misma preferencia que el impreso.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({LaboratoryServiceImp.class, TenantIdentifierResolver.class, AppConfig.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class LaboratoryReportFlagsTest {

    // El servicio los usa para firmar el logo/sello y para sembrar el catálogo en
    // el onboarding; nada de eso es lo que se prueba acá.
    @MockitoBean FileStorageService fileStorageService;
    @MockitoBean CatalogSeeder catalogSeeder;
    @MockitoBean AccountSeeder accountSeeder;

    @Autowired LaboratoryService laboratoryService;
    @Autowired LaboratoryRepository laboratoryRepository;
    @PersistenceContext EntityManager entityManager;

    private Long laboratoryId;

    @BeforeEach
    void setUp() {
        Laboratory laboratory = new Laboratory();
        laboratory.setName("Laboratorio de Prueba");
        laboratory = laboratoryRepository.save(laboratory);
        laboratoryId = laboratory.getId();
        TenantContext.setLaboratoryId(laboratoryId);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    /** Deja la columna en el valor dado y vacía el contexto para leer de la base. */
    private void storeFlag(Boolean value) {
        Laboratory laboratory = laboratoryRepository.findById(laboratoryId).orElseThrow();
        laboratory.setShowReportRangeFlags(value);
        laboratoryRepository.save(laboratory);
        entityManager.flush();
        entityManager.clear();
    }

    /** Cuerpo mínimo de un PUT: el nombre es obligatorio, el resto va nulo. */
    private LaboratoryDTO putBody() {
        LaboratoryDTO dto = new LaboratoryDTO();
        dto.setName("Laboratorio de Prueba");
        return dto;
    }

    // Un laboratorio que ya existía cuando se agregó la columna la tiene en nulo. No
    // se le puede apagar el reporte sin que nadie lo pida: nulo significa encendido,
    // y el cliente nunca debería tener que conocer ese valor por defecto.
    @Test
    void aLaboratoryThatNeverSetThePreferenceReadsAsOn() {
        storeFlag(null);

        assertEquals(Boolean.TRUE, laboratoryService.getLaboratory().getShowReportRangeFlags(),
                "sin valor guardado, las alertas siguen encendidas");
    }

    // Un laboratorio recién registrado nace con las alertas encendidas.
    @Test
    void aNewLaboratoryStartsWithTheAlertsOn() {
        assertEquals(Boolean.TRUE, laboratoryService.getLaboratory().getShowReportRangeFlags());
    }

    // El caso que motiva la feature: el laboratorio no quiere que el reporte
    // interprete el resultado del paciente, solo el número y su rango.
    @Test
    void turningTheAlertsOffPersists() {
        LaboratoryDTO dto = putBody();
        dto.setShowReportRangeFlags(false);

        assertEquals(Boolean.FALSE, laboratoryService.updateLaboratory(dto, laboratoryId).getShowReportRangeFlags());

        entityManager.flush();
        entityManager.clear();
        assertEquals(Boolean.FALSE, laboratoryService.getLaboratory().getShowReportRangeFlags(),
                "la preferencia debe seguir apagada al releerla");
    }

    @Test
    void turningTheAlertsBackOnPersists() {
        storeFlag(false);
        LaboratoryDTO dto = putBody();
        dto.setShowReportRangeFlags(true);

        assertEquals(Boolean.TRUE, laboratoryService.updateLaboratory(dto, laboratoryId).getShowReportRangeFlags());

        entityManager.flush();
        entityManager.clear();
        assertEquals(Boolean.TRUE, laboratoryService.getLaboratory().getShowReportRangeFlags());
    }

    // La regresión que importa: modelMapper copia los nulos encima, así que un
    // cliente que todavía no conoce el campo (o cualquier cuerpo parcial) borraría
    // la preferencia sin querer. Omitirla tiene que conservarla.
    @Test
    void anUpdateThatOmitsThePreferenceKeepsIt() {
        storeFlag(false);

        LaboratoryDTO dto = putBody();
        dto.setPhone("2233-4455");
        assertNull(dto.getShowReportRangeFlags(), "el cuerpo del cliente viejo no trae el campo");

        LaboratoryDTO updated = laboratoryService.updateLaboratory(dto, laboratoryId);

        assertEquals("2233-4455", updated.getPhone(), "lo que sí venía en el cuerpo se guarda");
        assertEquals(Boolean.FALSE, updated.getShowReportRangeFlags(),
                "omitir el campo no puede volver a encender las alertas");

        entityManager.flush();
        entityManager.clear();
        assertEquals(Boolean.FALSE, laboratoryService.getLaboratory().getShowReportRangeFlags());
    }

    // Nadie puede tocarle la configuración a otro laboratorio.
    @Test
    void aCallerCannotChangeAnotherLaboratorysPreference() {
        storeFlag(false);
        LaboratoryDTO dto = putBody();
        dto.setShowReportRangeFlags(true);

        assertThrows(Exception.class, () -> laboratoryService.updateLaboratory(dto, laboratoryId + 1));

        assertEquals(Boolean.FALSE, laboratoryService.getLaboratory().getShowReportRangeFlags(),
                "el laboratorio ajeno queda como estaba");
    }

    // El reporte público del paciente (r/{token}, sin login) tiene que respetar la
    // misma preferencia que el impreso. toLab es privado y el servicio arrastra once
    // dependencias que no hacen falta para mapear el membrete, así que se instancia
    // vacío y se invoca el mapeo directo: lo que se cuida acá es que el valor llegue
    // a PublicReportDTO.Lab y no se pierda en el constructor posicional.
    private PublicReportDTO.Lab mapLab(LaboratoryDTO dto) throws Exception {
        PublicReportServiceImp service = new PublicReportServiceImp(
                null, null, null, null, null, null, null, null, null, null, null);
        Method toLab = PublicReportServiceImp.class.getDeclaredMethod("toLab", LaboratoryDTO.class);
        toLab.setAccessible(true);
        return (PublicReportDTO.Lab) toLab.invoke(service, dto);
    }

    @Test
    void thePublicReportCarriesThePreference() throws Exception {
        storeFlag(false);

        PublicReportDTO.Lab lab = mapLab(laboratoryService.getLaboratory());

        assertEquals(Boolean.FALSE, lab.getShowReportRangeFlags());
        assertEquals("Laboratorio de Prueba", lab.getName(),
                "el resto del membrete no se corrió de posición");
    }

    @Test
    void thePublicReportReportsOnWhenThePreferenceWasNeverSet() throws Exception {
        storeFlag(null);

        assertEquals(Boolean.TRUE, mapLab(laboratoryService.getLaboratory()).getShowReportRangeFlags());
    }
}
