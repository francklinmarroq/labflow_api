package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.ReferringPhysicianDTO;
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
 * El médico solicitante de una orden: se escribe por NOMBRE, el que no exista se
 * da de alta solo al guardar, vaciarlo quita el médico de la orden sin borrarlo
 * del catálogo, y la orden reporta siempre el nombre vigente.
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
class OrderReferringPhysicianTest {

    @MockitoBean InvoiceService invoiceService;

    @Autowired LabOrderService labOrderService;
    @Autowired ReferringPhysicianService referringPhysicianService;
    @Autowired ReferringPhysicianRepository referringPhysicianRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired CustomerRepository customerRepository;

    private Customer customer;

    @BeforeEach
    void setUp() {
        Laboratory laboratory = new Laboratory();
        laboratory.setName("Laboratorio de Prueba");
        laboratory = laboratoryRepository.save(laboratory);
        TenantContext.setLaboratoryId(laboratory.getId());

        customer = new Customer();
        customer.setName("Paciente de Prueba");
        customer.setAgeInDays(30 * 365);
        customer = customerRepository.save(customer);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    private LabOrderDTO newOrder(String physicianName) {
        LabOrderDTO dto = new LabOrderDTO();
        dto.setCustomerId(customer.getId());
        dto.setStatus(OrderStatus.PENDING);
        dto.setReferringPhysician(physicianName);
        return labOrderService.createOrder(dto);
    }

    private LabOrderDTO setPhysician(LabOrderDTO order, String physicianName) {
        LabOrderDTO dto = new LabOrderDTO();
        dto.setCustomerId(order.getCustomerId());
        dto.setReferringPhysician(physicianName);
        return labOrderService.updateOrder(dto, order.getId());
    }

    // El caso que motiva la feature: se escribe el médico al levantar la orden, sin
    // haber pasado antes por ningún catálogo.
    @Test
    void creatingWithANewNameEnrolsThePhysicianAndLinksTheOrder() {
        LabOrderDTO order = newOrder("Dra. Ana Fúnez");

        assertEquals("Dra. Ana Fúnez", order.getReferringPhysician());
        assertNotNull(order.getReferringPhysicianId());
        assertTrue(referringPhysicianRepository.findByNormalizedName("dra. ana funez").isPresent(),
                "queda disponible para las siguientes órdenes");
    }

    @Test
    void creatingWithAnExistingNameHoweverSpelledLinksToTheExistingEntry() {
        LabOrderDTO first = newOrder("Dra. Ana Fúnez");
        LabOrderDTO second = newOrder("dra.  ana funez");

        assertEquals(first.getReferringPhysicianId(), second.getReferringPhysicianId());
        assertEquals(1, referringPhysicianRepository.findAllByOrderByNameAsc().size(),
                "no se duplica por escribirlo distinto");
        assertEquals("Dra. Ana Fúnez", second.getReferringPhysician(),
                "se reporta el nombre del catálogo, no lo que se tecleó esta vez");
    }

    @Test
    void creatingWithNoNameLeavesTheOrderWithoutOneAndTheCatalogueUntouched() {
        for (String name : new String[]{null, "", "   "}) {
            LabOrderDTO order = newOrder(name);
            assertNull(order.getReferringPhysician());
            assertNull(order.getReferringPhysicianId());
        }
        assertTrue(referringPhysicianRepository.findAllByOrderByNameAsc().isEmpty());
    }

    @Test
    void thePhysicianCanBeAddedAfterwards() {
        LabOrderDTO order = newOrder(null);

        LabOrderDTO updated = setPhysician(order, "Dra. Ana Fúnez");

        assertEquals("Dra. Ana Fúnez", updated.getReferringPhysician());
        assertNotNull(updated.getReferringPhysicianId());
    }

    @Test
    void thePhysicianCanBeReplaced() {
        LabOrderDTO order = newOrder("Dra. Ana Fúnez");
        Long ana = order.getReferringPhysicianId();

        LabOrderDTO updated = setPhysician(order, "Dr. Carlos Mejía");

        assertEquals("Dr. Carlos Mejía", updated.getReferringPhysician());
        assertNotEquals(ana, updated.getReferringPhysicianId());
        assertEquals(2, referringPhysicianRepository.findAllByOrderByNameAsc().size(),
                "los dos siguen en el catálogo");
    }

    // Vaciar el médico de UNA orden no lo borra del catálogo ni toca a las demás
    // órdenes que lo tienen.
    @Test
    void clearingLeavesThePhysicianInTheCatalogueAndOtherOrdersUntouched() {
        LabOrderDTO first = newOrder("Dra. Ana Fúnez");
        LabOrderDTO second = newOrder("Dra. Ana Fúnez");

        LabOrderDTO cleared = setPhysician(first, "   ");

        assertNull(cleared.getReferringPhysician());
        assertNull(cleared.getReferringPhysicianId());
        assertEquals(1, referringPhysicianRepository.findAllByOrderByNameAsc().size(),
                "el médico sigue en el catálogo");
        LabOrderDTO otherReloaded = labOrderService.getOrderById(second.getId());
        assertEquals("Dra. Ana Fúnez", otherReloaded.getReferringPhysician());
    }

    // El punto de que la orden apunte al catálogo: corregir el nombre una vez lo
    // corrige en todas las órdenes que ya lo tenían, reportes incluidos.
    @Test
    void anOrderReportsThePhysiciansCurrentNameAfterARename() {
        LabOrderDTO order = newOrder("dra ana funez");
        Long physicianId = order.getReferringPhysicianId();

        referringPhysicianService.updatePhysician(
                new ReferringPhysicianDTO(null, "Dra. Ana Fúnez", null), physicianId);

        LabOrderDTO reloaded = labOrderService.getOrderById(order.getId());
        assertEquals("Dra. Ana Fúnez", reloaded.getReferringPhysician());
        assertEquals(physicianId, reloaded.getReferringPhysicianId());
    }

    // El id es solo lectura: lo que manda es el nombre. Mandar un id de otro médico
    // no debe cambiar a quién apunta la orden.
    @Test
    void anIdSentOnAWriteIsIgnored() {
        LabOrderDTO ana = newOrder("Dra. Ana Fúnez");
        LabOrderDTO carlosOrder = newOrder("Dr. Carlos Mejía");
        Long carlos = carlosOrder.getReferringPhysicianId();

        LabOrderDTO dto = new LabOrderDTO();
        dto.setCustomerId(ana.getCustomerId());
        dto.setReferringPhysician("Dra. Ana Fúnez");
        dto.setReferringPhysicianId(carlos);
        LabOrderDTO updated = labOrderService.updateOrder(dto, ana.getId());

        assertEquals("Dra. Ana Fúnez", updated.getReferringPhysician());
        assertEquals(ana.getReferringPhysicianId(), updated.getReferringPhysicianId());
    }

    // El listado reporta el médico de cada orden y sigue paginando igual que antes.
    @Test
    void theListingReportsEachOrdersPhysicianAndPagesAsBefore() {
        newOrder("Dra. Ana Fúnez");
        newOrder("Dr. Carlos Mejía");
        newOrder(null);

        var page = labOrderService.getAllOrders(0, 2, "requestedAt", "DESC", null, null);

        assertEquals(3L, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals(2, page.getTotalPages());
        assertFalse(page.isLastPage());

        var all = labOrderService.getAllOrders(0, 50, "requestedAt", "ASC", null, null);
        assertEquals(java.util.Arrays.asList("Dra. Ana Fúnez", "Dr. Carlos Mejía", null),
                all.getContent().stream().map(LabOrderDTO::getReferringPhysician).toList());
    }
}
