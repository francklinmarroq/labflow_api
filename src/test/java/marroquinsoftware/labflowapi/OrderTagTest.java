package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.OrderTag;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.LabOrderResponse;
import marroquinsoftware.labflowapi.payload.OrderTagDTO;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.OrderTagRepository;
import marroquinsoftware.labflowapi.service.InvoiceService;
import marroquinsoftware.labflowapi.service.LabOrderService;
import marroquinsoftware.labflowapi.service.LabOrderServiceImp;
import marroquinsoftware.labflowapi.service.OrderTagService;
import marroquinsoftware.labflowapi.service.OrderTagServiceImp;
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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Etiquetas de orden: se crean solas la primera vez que se escriben, se
 * reutilizan después aunque se escriban distinto, y sirven para filtrar el
 * listado de órdenes.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({LabOrderServiceImp.class, OrderTagServiceImp.class, TenantIdentifierResolver.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class OrderTagTest {

    // LabOrderServiceImp lo usa solo para anular la factura al cancelar la orden,
    // que no es lo que se prueba acá; se simula para no arrastrar toda la
    // facturación y la contabilidad al contexto.
    @MockitoBean InvoiceService invoiceService;

    @Autowired LabOrderService labOrderService;
    @Autowired OrderTagService orderTagService;
    @Autowired OrderTagRepository orderTagRepository;
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

    private LabOrderDTO newOrder(String... tagNames) {
        LabOrderDTO dto = new LabOrderDTO();
        dto.setCustomerId(customer.getId());
        dto.setStatus(OrderStatus.PENDING);
        dto.setTagNames(List.of(tagNames));
        return labOrderService.createOrder(dto);
    }

    // El caso que motiva la feature: el laboratorio trabaja con el seguro social y
    // en la primera orden alguien escribe "IHSS" sin haber creado nada antes.
    @Test
    void firstUseOfATagCreatesIt() {
        LabOrderDTO order = newOrder("IHSS");

        assertEquals(1, order.getTags().size());
        assertEquals("IHSS", order.getTags().get(0).getName());
        assertTrue(orderTagRepository.findByNormalizedName("ihss").isPresent(),
                "la etiqueta debe quedar guardada para reutilizarla");
    }

    // Y en las siguientes órdenes se reutiliza la MISMA etiqueta, aunque se escriba
    // con otras mayúsculas, con espacios de sobra o sin tildes: si no, el catálogo
    // se llenaría de variantes del mismo convenio y el filtro dejaría filas fuera.
    @Test
    void laterOrdersReuseTheSameTagRegardlessOfHowItIsTyped() {
        LabOrderDTO first = newOrder("Régimen  IHSS");
        LabOrderDTO second = newOrder("regimen ihss");

        assertEquals(1, orderTagRepository.findAllByOrderByNameAsc().size(),
                "las dos escrituras son la misma etiqueta");
        assertEquals(first.getTags().get(0).getId(), second.getTags().get(0).getId());
        // El nombre visible es el de la primera vez, con los espacios ya colapsados.
        assertEquals("Régimen IHSS", second.getTags().get(0).getName());
    }

    @Test
    void repeatedAndBlankNamesInTheSameOrderAreIgnored() {
        LabOrderDTO order = newOrder("IHSS", "  ", "ihss", "Convenio X");

        assertEquals(2, order.getTags().size());
        assertEquals(List.of("IHSS", "Convenio X"),
                order.getTags().stream().map(OrderTagDTO::getName).toList());
    }

    @Test
    void listingCanBeFilteredByTag() {
        LabOrderDTO ihss = newOrder("IHSS");
        newOrder("Particular");
        Long tagId = ihss.getTags().get(0).getId();

        LabOrderResponse filtered = labOrderService.getAllOrders(0, 50, "requestedAt", "DESC", null, tagId);
        assertEquals(1, filtered.getContent().size());
        assertEquals(ihss.getId(), filtered.getContent().get(0).getId());

        LabOrderResponse all = labOrderService.getAllOrders(0, 50, "requestedAt", "DESC", null, null);
        assertEquals(2, all.getContent().size(), "sin filtro se listan las dos órdenes");
    }

    // Actualizar la orden por otra cosa (cambiar de estado al ingresar resultados,
    // por ejemplo) no manda tagNames y no debe borrar las etiquetas ya puestas.
    @Test
    void updatingWithoutTagNamesLeavesTheTagsAlone() {
        LabOrderDTO order = newOrder("IHSS");

        LabOrderDTO update = new LabOrderDTO();
        update.setStatus(OrderStatus.IN_PROGRESS);
        LabOrderDTO updated = labOrderService.updateOrder(update, order.getId());

        assertEquals(1, updated.getTags().size());
        assertEquals("IHSS", updated.getTags().get(0).getName());
    }

    @Test
    void updatingWithAnEmptyListRemovesTheTags() {
        LabOrderDTO order = newOrder("IHSS");

        LabOrderDTO update = new LabOrderDTO();
        update.setStatus(OrderStatus.IN_PROGRESS);
        update.setTagNames(List.of());
        LabOrderDTO updated = labOrderService.updateOrder(update, order.getId());

        assertTrue(updated.getTags().isEmpty());
        assertTrue(orderTagRepository.findByNormalizedName("ihss").isPresent(),
                "quitarla de la orden no la borra del catálogo del laboratorio");
    }

    // Borrar una etiqueta en uso tiene que desengancharla de sus órdenes primero;
    // si no, el DELETE revienta por llave foránea.
    @Test
    void deletingATagInUseDetachesItFromItsOrders() {
        LabOrderDTO order = newOrder("IHSS");
        Long tagId = order.getTags().get(0).getId();

        assertDoesNotThrow(() -> orderTagService.deleteTag(tagId));
        assertTrue(orderTagRepository.findById(tagId).isEmpty());
        assertTrue(labOrderService.getOrderById(order.getId()).getTags().isEmpty(),
                "la orden sobrevive, solo pierde la clasificación");
    }

    @Test
    void renamingToAnExistingTagIsRejected() {
        Set<OrderTag> created = orderTagService.resolveOrCreate(List.of("IHSS", "Particular"));
        Long particularId = created.stream()
                .filter(t -> t.getName().equals("Particular")).findFirst().orElseThrow().getId();

        OrderTagDTO rename = new OrderTagDTO(particularId, "ihss", null, null);
        APIException error = assertThrows(APIException.class,
                () -> orderTagService.updateTag(rename, particularId));
        assertTrue(error.getMessage().contains("IHSS"));
    }

    @Test
    void tagListingCarriesHowManyOrdersUseEachTag() {
        newOrder("IHSS");
        newOrder("IHSS");
        newOrder("Particular");

        List<OrderTagDTO> tags = orderTagService.getAllTags().getContent();
        assertEquals(2, tags.size());
        assertEquals(2L, tags.stream().filter(t -> t.getName().equals("IHSS"))
                .findFirst().orElseThrow().getOrderCount());
        assertEquals(1L, tags.stream().filter(t -> t.getName().equals("Particular"))
                .findFirst().orElseThrow().getOrderCount());
    }
}
