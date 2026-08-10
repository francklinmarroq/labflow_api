package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.LabOrderResponse;

public interface LabOrderService {
    // status == null: órdenes activas (excluye canceladas). status != null: solo
    // las de ese estado (p. ej. CANCELLED para la pestaña de archivadas).
    LabOrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortDir, OrderStatus status);
    LabOrderDTO createOrder(LabOrderDTO dto);
    LabOrderDTO updateOrder(LabOrderDTO dto, Long id);
    LabOrderDTO getOrderById(Long id);

    /**
     * Cancela la orden (borrado lógico a CANCELLED) dejando motivo y auditoría.
     * Si la orden tiene una factura activa, la anula en cascada (revierte pagos y
     * emisión con contra-asientos); en ese caso el usuario debe tener además el
     * permiso de anular facturas.
     */
    LabOrderDTO cancelOrder(Long id, String reason);
}
