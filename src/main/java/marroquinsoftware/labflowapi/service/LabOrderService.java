package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.LabOrderResponse;
import marroquinsoftware.labflowapi.payload.OrderExportRowDTO;

import java.util.List;

public interface LabOrderService {
    // status == null: órdenes activas (excluye canceladas). status != null: solo
    // las de ese estado (p. ej. CANCELLED para la pestaña de archivadas).
    // tagId == null: todas; con valor, solo las órdenes con esa etiqueta.
    LabOrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                  OrderStatus status, Long tagId);
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

    /**
     * Exámenes y total a pagar de las órdenes indicadas, para exportarlas a Excel.
     * Devuelve una fila por orden encontrada, en el orden de los ids recibidos; las
     * que no existen o son de otro laboratorio se omiten.
     */
    List<OrderExportRowDTO> exportOrders(List<Long> orderIds);
}
