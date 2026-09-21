package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Invoice;
import marroquinsoftware.labflowapi.model.InvoiceStatus;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.Test;
import marroquinsoftware.labflowapi.model.TestConfig;
import marroquinsoftware.labflowapi.model.TestMethod;
import marroquinsoftware.labflowapi.payload.LabTestDTO;
import marroquinsoftware.labflowapi.repositories.InvoiceRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.TestConfigRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LabTestServiceImp implements LabTestService {

    @Autowired
    private LabOrderRepository labOrderRepository;

    @Autowired
    private LabTestRepository labTestRepository;

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private TestConfigRepository testConfigRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TestMethodService testMethodService;

    @Override
    public List<LabTestDTO> getTestsByOrder(Long orderId) {
        if (!labOrderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("LabOrder", "orderId", orderId);
        }
        return labTestRepository.findByOrder_Id(orderId).stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional
    public LabTestDTO addTestToOrder(Long orderId, LabTestDTO dto) {
        // Antes de construir nada: si la orden está facturada, no se toca.
        requireTestsUnlocked(orderId);
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("LabOrder", "orderId", orderId));
        Test test = testRepository.findById(dto.getTestId())
                .orElseThrow(() -> new ResourceNotFoundException("Test", "testId", dto.getTestId()));
        LabTest labTest = new LabTest();
        labTest.setOrder(order);
        labTest.setTest(test);
        labTest.setTestConfig(null);
        return toDTO(labTestRepository.save(labTest));
    }

    // Transaccional porque además de escribir el perfil lee la colección de métodos
    // para estampar el predeterminado: leer una colección perezosa fuera de una
    // transacción depende de que el open-in-view la mantenga viva, que es una
    // suposición que no hay por qué hacer acá.
    @Override
    @Transactional
    public LabTestDTO assignTestConfig(Long orderId, Long labTestId, Long testConfigId) {
        LabTest labTest = labTestRepository.findById(labTestId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "labTestId", labTestId));
        if (!labTest.getOrder().getId().equals(orderId)) {
            throw new APIException("El examen no pertenece a la orden indicada. Recargue la página e intente de nuevo.");
        }
        TestConfig testConfig = testConfigRepository.findById(testConfigId)
                .orElseThrow(() -> new ResourceNotFoundException("TestConfig", "testConfigId", testConfigId));
        if (!testConfig.getTest().getId().equals(labTest.getTest().getId())) {
            throw new APIException("El perfil '" + testConfig.getName() + "' no corresponde al examen '" + labTest.getTest().getName() + "'.");
        }
        labTest.setTestConfig(testConfig);
        // Acá es donde un examen adquiere su método: al asignarle el perfil se le
        // estampa el predeterminado de ese perfil, de modo que quien abre la orden lo
        // encuentra ya puesto en vez de tener que escribirlo cada vez. NUNCA se
        // sobrescribe: si el examen ya dice con qué se corrió, cambiar de perfil no
        // puede reescribir en silencio lo que el técnico indicó. Y cambiar el
        // predeterminado del perfil después no alcanza a los exámenes ya estampados:
        // lo que una orden dice que se usó no se toca.
        if (labTest.getMethod() == null) {
            testConfig.getMethods().stream()
                    .filter(TestMethod::isDefaultMethod)
                    .findFirst()
                    .ifPresent(labTest::setMethod);
        }
        return toDTO(labTestRepository.save(labTest));
    }

    @Override
    public LabTestDTO updateNotes(Long orderId, Long labTestId, String notes) {
        LabTest labTest = labTestRepository.findById(labTestId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "labTestId", labTestId));
        if (!labTest.getOrder().getId().equals(orderId)) {
            throw new APIException("El examen no pertenece a la orden indicada. Recargue la página e intente de nuevo.");
        }
        labTest.setNotes(notes);
        return toDTO(labTestRepository.save(labTest));
    }

    @Override
    public LabTestDTO updateSampleType(Long orderId, Long labTestId, String sampleType) {
        LabTest labTest = labTestRepository.findById(labTestId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "labTestId", labTestId));
        if (!labTest.getOrder().getId().equals(orderId)) {
            throw new APIException("El examen no pertenece a la orden indicada. Recargue la página e intente de nuevo.");
        }
        labTest.setSampleType(sampleType);
        return toDTO(labTestRepository.save(labTest));
    }

    /**
     * Fija el método del examen POR NOMBRE, que es lo único que el cliente manda.
     * El nombre se resuelve contra los métodos del perfil del examen; el que el
     * perfil no tenga se le agrega y queda disponible para las siguientes órdenes.
     * Elegirlo además lo deja como el predeterminado del perfil: así la técnica que
     * el laboratorio usa de verdad se asienta sola, sin que nadie la configure.
     */
    @Override
    @Transactional
    public LabTestDTO updateMethod(Long orderId, Long labTestId, String method) {
        LabTest labTest = labTestRepository.findById(labTestId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "labTestId", labTestId));
        if (!labTest.getOrder().getId().equals(orderId)) {
            throw new APIException("El examen no pertenece a la orden indicada. Recargue la página e intente de nuevo.");
        }
        if (method == null || method.isBlank()) {
            // Dejar de indicar la técnica no dice nada sobre cuál es la usual: el
            // método sigue en el perfil y el predeterminado del perfil no se toca.
            labTest.setMethod(null);
            return toDTO(labTestRepository.save(labTest));
        }
        TestConfig testConfig = labTest.getTestConfig();
        if (testConfig == null) {
            // Los métodos son del perfil: sin perfil no hay de dónde elegir ni dónde
            // guardar uno nuevo. Es un estado pasajero —el frontend asigna el perfil
            // en cuanto agrega el examen— así que se rechaza diciendo qué falta, en
            // vez de descartar el método en silencio.
            throw new APIException("Primero defina el perfil del examen '" + labTest.getTest().getName()
                    + "' en esta orden: los métodos son del perfil, así que hasta entonces no hay de dónde elegir.");
        }
        TestMethod resolved = testMethodService.resolveOrCreate(testConfig, method);
        labTest.setMethod(resolved);
        testMethodService.markAsDefault(resolved);
        return toDTO(labTestRepository.save(labTest));
    }

    @Override
    @Transactional
    public LabTestDTO removeTestFromOrder(Long orderId, Long testId) {
        // Antes de borrar nada: si la orden está facturada, no se toca.
        requireTestsUnlocked(orderId);
        LabTest labTest = labTestRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "testId", testId));
        if (!labTest.getOrder().getId().equals(orderId)) {
            throw new APIException("El examen no pertenece a la orden indicada. Recargue la página e intente de nuevo.");
        }
        labTestRepository.delete(labTest);
        return toDTO(labTest);
    }

    /**
     * Rechaza cambiar los exámenes de una orden que ya tiene factura viva (no
     * anulada). Una factura es un documento fiscal: congela el nombre y el precio
     * de cada examen que cobra, así que agregar o quitar exámenes después la deja
     * diciendo algo distinto de lo que la orden contiene, sin que nada lo detecte.
     *
     * <p>Usa la misma lectura que LabOrderServiceImp.cancelOrder para que los dos
     * lugares que razonan sobre "factura viva" lo hagan igual. Único sitio donde
     * se escribe el mensaje: nombra la factura para que quien atiende pueda ir a
     * anularla, que es la ÚNICA vía de corrección (anular → corregir → refacturar).
     * No es un permiso: es una propiedad de la orden y no hay excepción para nadie.
     */
    private void requireTestsUnlocked(Long orderId) {
        Invoice invoice = invoiceRepository
                .findFirstByOrderIdAndStatusNotOrderByIssuedAtDesc(orderId, InvoiceStatus.ANULADA)
                .orElse(null);
        if (invoice != null) {
            throw new APIException("Los exámenes de esta orden ya están facturados en la factura "
                    + invoice.getInvoiceNumber() + " y no se pueden agregar ni quitar. "
                    + "Para corregirlos, anule esa factura, ajuste los exámenes y emita una factura nueva.");
        }
    }

    /**
     * Se arma campo a campo y no con el ModelMapper (como hace
     * LabOrderServiceImp.toDTO) porque el método dejó de ser texto de la fila: el
     * DTO reporta el NOMBRE y la entidad guarda la asociación, y dejar que el mapeo
     * automático resuelva esa diferencia es exactamente la clase de suposición que
     * funciona en H2 y se cae en la imagen nativa.
     */
    private LabTestDTO toDTO(LabTest labTest) {
        LabTestDTO dto = new LabTestDTO();
        dto.setId(labTest.getId());
        dto.setOrderId(labTest.getOrder().getId());
        dto.setTestId(labTest.getTest().getId());
        dto.setTestConfigId(labTest.getTestConfig() != null ? labTest.getTestConfig().getId() : null);
        dto.setNotes(labTest.getNotes());
        dto.setSampleType(labTest.getSampleType());
        // El método se reporta por NOMBRE, en el mismo campo de siempre, para que un
        // cliente escrito antes de que vivieran en el perfil siga funcionando igual.
        // Es el nombre VIGENTE en el perfil: por eso corregirlo ahí corrige también
        // las órdenes ya levantadas. El id va aparte y es solo lectura.
        TestMethod method = labTest.getMethod();
        dto.setMethod(method != null ? method.getName() : null);
        dto.setMethodId(method != null ? method.getId() : null);
        return dto;
    }
}
