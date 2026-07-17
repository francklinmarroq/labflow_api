package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.Test;
import marroquinsoftware.labflowapi.model.TestConfig;
import marroquinsoftware.labflowapi.payload.LabTestDTO;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.TestConfigRepository;
import marroquinsoftware.labflowapi.repositories.TestRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    private ModelMapper modelMapper;

    @Override
    public List<LabTestDTO> getTestsByOrder(Long orderId) {
        if (!labOrderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("LabOrder", "orderId", orderId);
        }
        return labTestRepository.findByOrder_Id(orderId).stream().map(this::toDTO).toList();
    }

    @Override
    public LabTestDTO addTestToOrder(Long orderId, LabTestDTO dto) {
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
    public LabTestDTO removeTestFromOrder(Long orderId, Long testId) {
        LabTest labTest = labTestRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "testId", testId));
        if (!labTest.getOrder().getId().equals(orderId)) {
            throw new APIException("El examen no pertenece a la orden indicada. Recargue la página e intente de nuevo.");
        }
        labTestRepository.delete(labTest);
        return toDTO(labTest);
    }

    private LabTestDTO toDTO(LabTest labTest) {
        LabTestDTO dto = modelMapper.map(labTest, LabTestDTO.class);
        dto.setOrderId(labTest.getOrder().getId());
        dto.setTestId(labTest.getTest().getId());
        dto.setTestConfigId(labTest.getTestConfig() != null ? labTest.getTestConfig().getId() : null);
        return dto;
    }
}
