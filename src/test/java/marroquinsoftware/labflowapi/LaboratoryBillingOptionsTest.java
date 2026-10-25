package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import marroquinsoftware.labflowapi.config.AppConfig;
import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.payload.LaboratoryDTO;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.service.AccountSeeder;
import marroquinsoftware.labflowapi.service.CatalogSeeder;
import marroquinsoftware.labflowapi.service.FileStorageService;
import marroquinsoftware.labflowapi.service.LaboratoryService;
import marroquinsoftware.labflowapi.service.LaboratoryServiceImp;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opciones de facturación del laboratorio: el interruptor "Facturar sin CAI" y el
 * papel de la factura. Nacen apagado y en A5, nunca se responden nulos, un valor
 * de papel desconocido se rechaza y un PUT que no los trae los conserva (igual
 * que el correlativo interno, que ni siquiera viaja en el DTO).
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
class LaboratoryBillingOptionsTest {

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
        laboratoryId = laboratoryRepository.save(laboratory).getId();
        TenantContext.setLaboratoryId(laboratoryId);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    private void store(Boolean withoutCai, String paperSize, Long nextNumber) {
        Laboratory laboratory = laboratoryRepository.findById(laboratoryId).orElseThrow();
        laboratory.setInvoiceWithoutCai(withoutCai);
        laboratory.setInvoicePaperSize(paperSize);
        laboratory.setNonFiscalNextNumber(nextNumber);
        laboratoryRepository.save(laboratory);
        entityManager.flush();
        entityManager.clear();
    }

    private LaboratoryDTO putBody() {
        LaboratoryDTO dto = new LaboratoryDTO();
        dto.setName("Laboratorio de Prueba");
        return dto;
    }

    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    // Laboratorio que ya existía cuando se agregaron las columnas: ambas en nulo.
    @Test
    void aLaboratoryThatNeverSetThemReadsAsOffAndA5() {
        store(null, null, null);

        LaboratoryDTO dto = laboratoryService.getLaboratory();

        assertEquals(Boolean.FALSE, dto.getInvoiceWithoutCai());
        assertEquals("A5_LANDSCAPE", dto.getInvoicePaperSize());
    }

    @Test
    void aNewLaboratoryStartsOffAndA5() {
        LaboratoryDTO dto = laboratoryService.getLaboratory();

        assertEquals(Boolean.FALSE, dto.getInvoiceWithoutCai());
        assertEquals("A5_LANDSCAPE", dto.getInvoicePaperSize());
    }

    @Test
    void turningTheSwitchOnPersists() {
        LaboratoryDTO dto = putBody();
        dto.setInvoiceWithoutCai(true);

        assertEquals(Boolean.TRUE, laboratoryService.updateLaboratory(dto, laboratoryId).getInvoiceWithoutCai());
        reload();
        assertEquals(Boolean.TRUE, laboratoryService.getLaboratory().getInvoiceWithoutCai());
    }

    @Test
    void choosingAThermalPrinterPersists() {
        LaboratoryDTO dto = putBody();
        dto.setInvoicePaperSize("THERMAL_58");

        assertEquals("THERMAL_58", laboratoryService.updateLaboratory(dto, laboratoryId).getInvoicePaperSize());
        reload();
        assertEquals("THERMAL_58", laboratoryService.getLaboratory().getInvoicePaperSize());
    }

    @Test
    void anUnknownPaperSizeIsRejectedAndChangesNothing() {
        store(false, "THERMAL_80", null);
        LaboratoryDTO dto = putBody();
        dto.setPhone("2233-4455");
        dto.setInvoicePaperSize("LETTER");

        // La validación del controlador (@Valid) y la del servicio lo rechazan.
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertFalse(validator.validateProperty(dto, "invoicePaperSize").isEmpty());
        }
        assertThrows(APIException.class, () -> laboratoryService.updateLaboratory(dto, laboratoryId));

        reload();
        LaboratoryDTO stored = laboratoryService.getLaboratory();
        assertEquals("THERMAL_80", stored.getInvoicePaperSize());
        assertNotEquals("2233-4455", stored.getPhone());
    }

    // Un cliente que todavía no conoce los campos no puede resetearlos sin querer,
    // y ningún PUT toca el correlativo interno de los documentos sin CAI.
    @Test
    void anUpdateThatOmitsThemKeepsThemAndTheCorrelative() {
        store(true, "THERMAL_80", 42L);

        LaboratoryDTO dto = putBody();
        dto.setPhone("2233-4455");
        LaboratoryDTO updated = laboratoryService.updateLaboratory(dto, laboratoryId);

        assertEquals("2233-4455", updated.getPhone());
        assertEquals(Boolean.TRUE, updated.getInvoiceWithoutCai());
        assertEquals("THERMAL_80", updated.getInvoicePaperSize());
        reload();
        assertEquals(42L, laboratoryRepository.findById(laboratoryId).orElseThrow().getNonFiscalNextNumber());
    }
}
