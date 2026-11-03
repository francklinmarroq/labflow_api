package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que la exportación de órdenes necesita y el listado no trae: los exámenes y
 * el total a pagar de la orden. El resto de columnas (paciente, etiquetas,
 * médico…) el cliente ya lo tiene del listado.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderExportRowDTO {

    /** De dónde sale el total: lo que cobra su factura vigente o el catálogo de hoy. */
    public enum TotalSource { INVOICED, CATALOG }

    private Long orderId;
    /** Nombres de los exámenes, en el orden en que se agregaron a la orden. */
    private List<String> testNames;
    private int testCount;
    /**
     * Facturada: lo cobrado por sus exámenes menos su descuento por edad. Sin
     * factura vigente: suma de los precios actuales del catálogo.
     */
    private BigDecimal total;
    private TotalSource totalSource;
    private String invoiceNumber;
    private String invoiceStatus;
    private String invoiceStatusLabel;
}
