package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.TestConfig;
import marroquinsoftware.labflowapi.payload.LabTestDTO;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.TestConfigRepository;
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
    private TestConfigRepository testConfigRepository;

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
        TestConfig testConfig = testConfigRepository.findById(dto.getTestConfigId())
                .orElseThrow(() -> new ResourceNotFoundException("TestConfig", "testConfigId", dto.getTestConfigId()));
        LabTest test = new LabTest();
        test.setOrder(order);
        test.setTestConfig(testConfig);
        return toDTO(labTestRepository.save(test));
    }

    @Override
    public LabTestDTO removeTestFromOrder(Long orderId, Long testId) {
        LabTest test = labTestRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "testId", testId));
        if (!test.getOrder().getId().equals(orderId)) {
            throw new APIException("Test with id: " + testId + " does not belong to order with id: " + orderId);
        }
        labTestRepository.delete(test);
        return toDTO(test);
    }

    private LabTestDTO toDTO(LabTest test) {
        LabTestDTO dto = new LabTestDTO();
        dto.setId(test.getId());
        dto.setOrderId(test.getOrder().getId());
        dto.setTestConfigId(test.getTestConfig().getId());
        return dto;
    }
}
