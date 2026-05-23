package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.LabOrderResponse;

public interface LabOrderService {
    LabOrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortDir);
    LabOrderDTO createOrder(LabOrderDTO dto);
    LabOrderDTO updateOrder(LabOrderDTO dto, Long id);
    LabOrderDTO deleteOrder(Long id);
}
