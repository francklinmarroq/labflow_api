package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.ReferringPhysician;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.ReferringPhysicianDTO;
import marroquinsoftware.labflowapi.payload.ReferringPhysicianResponse;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.ReferringPhysicianRepository;
import marroquinsoftware.labflowapi.service.InvoiceService;
import marroquinsoftware.labflowapi.service.LabOrderService;
import marroquinsoftware.labflowapi.service.LabOrderServiceImp;
import marroquinsoftware.labflowapi.service.OrderTagServiceImp;
import marroquinsoftware.labflowapi.service.ReferringPhysicianService;
import marroquinsoftware.labflowapi.service.ReferringPhysicianServiceImp;
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
 * Catálogo de médicos solicitantes: se crean solos la primera vez que se escriben
 * en una orden, se reutilizan después aunque se escriban distinto, se les corrige
 * el nombre desde el catálogo y borrarlos no toca las órdenes.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({LabOrderServiceImp.class, OrderTagServiceImp.class, ReferringPhysicianServiceImp.class,
        TenantIdentifierResolver.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class ReferringPhysicianTest {

    // LabOrderServiceImp lo usa solo para anular la factura al cancelar la orden,
    // que no es lo que se prueba acá.
    @MockitoBean InvoiceService invoiceService;

    @PersistenceContext EntityManager entityManager;

    @Autowired LabOrderService labOrderService;
    @Autowired ReferringPhysicianService referringPhysicianService;
    @Autowired ReferringPhysicianRepository referringPhysicianRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired CustomerRepository customerRepository;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = newLaboratoryWithCustomer("Laboratorio de Prueba");
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    private Customer newLaboratoryWithCustomer(String labName) {
        Laboratory laboratory = new Laboratory();
        laboratory.setName(labName);
        laboratory = laboratoryRepository.save(laboratory);
        TenantContext.setLaboratoryId(laboratory.getId());

        Customer created = new Customer();
        created.setName("Paciente de Prueba");
        created.setAgeInDays(30 * 365);
        return customerRepository.save(created);
    }

    private LabOrderDTO newOrder(Customer patient, String physicianName) {
        LabOrderDTO dto = new LabOrderDTO();
        dto.setCustomerId(patient.getId());
        dto.setStatus(OrderStatus.PENDING);
        dto.setReferringPhysician(physicianName);
        return labOrderService.createOrder(dto);
    }

    // El caso que motiva la feature: nadie dio de alta al médico, se escribió en la
    // primera orden y con eso queda disponible.
    @Test
    void firstUseOfAPhysicianCreatesIt() {
        ReferringPhysician physician = referringPhysicianService.resolveOrCreate("Dra. Ana Fúnez");

        assertNotNull(physician.getId());
        assertEquals("Dra. Ana Fúnez", physician.getName());
        assertTrue(referringPhysicianRepository.findByNormalizedName("dra. ana funez").isPresent(),
                "el médico debe quedar guardado para reutilizarlo");
    }

    // Y se reutiliza el MISMO médico aunque se escriba con otras mayúsculas, sin
    // tildes o con espacios de sobra: si no, el catálogo se llenaría de variantes de
    // la misma persona.
    @Test
    void laterUsesReuseTheSamePhysicianRegardlessOfHowItIsTyped() {
        ReferringPhysician first = referringPhysicianService.resolveOrCreate("Dra. Ana  Fúnez");
        ReferringPhysician second = referringPhysicianService.resolveOrCreate("dra. ana funez");

        assertEquals(first.getId(), second.getId(), "las dos escrituras son el mismo médico");
        assertEquals(1, referringPhysicianRepository.findAllByOrderByNameAsc().size());
        // El nombre visible es el de la primera vez, con los espacios ya colapsados.
        assertEquals("Dra. Ana Fúnez", second.getName());
    }

    @Test
    void aBlankNameCreatesNothingAndReturnsNone() {
        assertNull(referringPhysicianService.resolveOrCreate(null));
        assertNull(referringPhysicianService.resolveOrCreate(""));
        assertNull(referringPhysicianService.resolveOrCreate("   "));
        assertTrue(referringPhysicianRepository.findAllByOrderByNameAsc().isEmpty());
    }

    /**
     * La unicidad del nombre es POR laboratorio: dos laboratorios pueden tener cada
     * uno al mismo médico, y ninguno ve el del otro.
     *
     * <p>La ficha del otro laboratorio se inserta con SQL nativo a propósito, por lo
     * mismo que {@code BillingClientTest.theSameRtnIsAcceptedInAnotherLaboratory}: el
     * tenant de una sesión de Hibernate queda fijado cuando la sesión se abre, y en
     * un {@code @DataJpaTest} toda la prueba corre dentro de una sola, así que mover
     * el {@link TenantContext} a mitad de camino no cambia por cuál laboratorio
     * filtra. El SQL nativo se salta el filtro (ver AGENTS.md), que es justo lo que
     * hace falta para poner la fila del otro laboratorio y comprobar las dos cosas:
     * que la restricción única la acepta —es compuesta, no sobre el nombre solo— y
     * que desde acá sigue sin verse.
     */
    @Test
    void theSamePhysicianInTwoLaboratoriesGivesTwoEntries() {
        ReferringPhysician mine = referringPhysicianService.resolveOrCreate("Dr. Carlos Mejía");

        Laboratory other = new Laboratory();
        other.setName("Otro Laboratorio");
        other = laboratoryRepository.save(other);

        entityManager.createNativeQuery("""
                        insert into referring_physicians (laboratory_id, name, normalized_name)
                        values (:laboratoryId, :name, :normalizedName)
                        """)
                .setParameter("laboratoryId", other.getId())
                .setParameter("name", "Dr. Carlos Mejía")
                .setParameter("normalizedName", "dr. carlos mejia")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // Existe en la tabla, bajo el otro laboratorio...
        assertEquals(1L, ((Number) entityManager.createNativeQuery(
                        "select count(*) from referring_physicians where laboratory_id = :laboratoryId")
                .setParameter("laboratoryId", other.getId())
                .getSingleResult()).longValue());

        // ...y desde este laboratorio no se lista ni se reutiliza: sigue habiendo uno.
        assertEquals(1, referringPhysicianRepository.findAllByOrderByNameAsc().size(),
                "cada laboratorio ve solo el suyo");
        assertEquals(mine.getId(), referringPhysicianService.resolveOrCreate("dr. carlos mejia").getId(),
                "escribirlo acá reutiliza el de este laboratorio, nunca el del otro");
    }

    @Test
    void listingIsOrderedByNameAndCountsOrders() {
        newOrder(customer, "Dra. Ana Fúnez");
        newOrder(customer, "Dra. Ana Fúnez");
        referringPhysicianService.resolveOrCreate("Dr. Carlos Mejía"); // sin órdenes

        ReferringPhysicianResponse listing = referringPhysicianService.getAllPhysicians();

        assertEquals(2, listing.getContent().size());
        assertEquals("Dr. Carlos Mejía", listing.getContent().get(0).getName());
        assertEquals(0L, listing.getContent().get(0).getOrderCount(),
                "un médico sin órdenes cuenta cero, no se omite");
        assertEquals("Dra. Ana Fúnez", listing.getContent().get(1).getName());
        assertEquals(2L, listing.getContent().get(1).getOrderCount());
    }

    @Test
    void creatingADuplicateIsRefusedNamingTheExistingOne() {
        referringPhysicianService.createPhysician(new ReferringPhysicianDTO(null, "Dra. Ana Fúnez", null));

        APIException error = assertThrows(APIException.class, () -> referringPhysicianService
                .createPhysician(new ReferringPhysicianDTO(null, "dra. ana funez", null)));

        assertTrue(error.getMessage().contains("Dra. Ana Fúnez"), error.getMessage());
        assertEquals(1, referringPhysicianRepository.findAllByOrderByNameAsc().size());
    }

    @Test
    void renamingOntoAnotherPhysicianIsRefused() {
        ReferringPhysicianDTO ana = referringPhysicianService
                .createPhysician(new ReferringPhysicianDTO(null, "Dra. Ana Fúnez", null));
        referringPhysicianService.createPhysician(new ReferringPhysicianDTO(null, "Dr. Carlos Mejía", null));

        APIException error = assertThrows(APIException.class, () -> referringPhysicianService
                .updatePhysician(new ReferringPhysicianDTO(null, "dr. carlos mejia", null), ana.getId()));

        assertTrue(error.getMessage().contains("Dr. Carlos Mejía"), error.getMessage());
        assertEquals("Dra. Ana Fúnez", referringPhysicianRepository.findById(ana.getId()).orElseThrow().getName());
    }

    // Corregir solo tildes o mayúsculas es el MISMO médico arreglando cómo se
    // escribe, así que sí se permite aunque choque consigo mismo.
    @Test
    void correctingOnlyAccentsOrCaseOfTheSameEntryIsAllowed() {
        ReferringPhysicianDTO ana = referringPhysicianService
                .createPhysician(new ReferringPhysicianDTO(null, "dra ana funez", null));

        ReferringPhysicianDTO renamed = referringPhysicianService
                .updatePhysician(new ReferringPhysicianDTO(null, "Dra. Ana Fúnez", null), ana.getId());

        assertEquals(ana.getId(), renamed.getId());
        assertEquals("Dra. Ana Fúnez", renamed.getName());
    }

    // Borrar un médico NO borra ni cancela las órdenes que lo tenían: solo dejan de
    // indicar médico solicitante.
    @Test
    void deletingAPhysicianLeavesTheOrdersIntactWithoutOne() {
        LabOrderDTO order = newOrder(customer, "Dra. Ana Fúnez");
        Long physicianId = order.getReferringPhysicianId();
        assertNotNull(physicianId);

        referringPhysicianService.deletePhysician(physicianId);

        assertTrue(referringPhysicianRepository.findById(physicianId).isEmpty());
        LabOrderDTO reloaded = labOrderService.getOrderById(order.getId());
        assertNull(reloaded.getReferringPhysician());
        assertNull(reloaded.getReferringPhysicianId());
        assertEquals(OrderStatus.PENDING, reloaded.getStatus(), "la orden sigue viva e intacta");
    }

    @Test
    void deletingAnUnusedPhysicianJustRemovesIt() {
        ReferringPhysicianDTO carlos = referringPhysicianService
                .createPhysician(new ReferringPhysicianDTO(null, "Dr. Carlos Mejía", null));

        referringPhysicianService.deletePhysician(carlos.getId());

        assertTrue(referringPhysicianRepository.findAllByOrderByNameAsc().isEmpty());
    }
}
