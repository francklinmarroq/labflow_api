package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Invoice;
import marroquinsoftware.labflowapi.model.InvoiceStatus;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.Test;
import marroquinsoftware.labflowapi.model.TestConfig;
import marroquinsoftware.labflowapi.payload.LabTestDTO;
import marroquinsoftware.labflowapi.repositories.InvoiceRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.TestConfigRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import org.modelmapper.ModelMapper;
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
    private ModelMapper modelMapper;

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

    @Override
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

    @Override
    public LabTestDTO updateMethod(Long orderId, Long labTestId, String method) {
        LabTest labTest = labTestRepository.findById(labTestId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "labTestId", labTestId));
        if (!labTest.getOrder().getId().equals(orderId)) {
            throw new APIException("El examen no pertenece a la orden indicada. Recargue la página e intente de nuevo.");
        }
        labTest.setMethod(method);
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

    private LabTestDTO toDTO(LabTest labTest) {
        LabTestDTO dto = modelMapper.map(labTest, LabTestDTO.class);
        dto.setOrderId(labTest.getOrder().getId());
        dto.setTestId(labTest.getTest().getId());
        dto.setTestConfigId(labTest.getTestConfig() != null ? labTest.getTestConfig().getId() : null);
        return dto;
    }
}
