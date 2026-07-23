package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Cuentas por cobrar: facturas con saldo abierto y el total adeudado. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceivablesResponse {
    private List<InvoiceDTO> content;
    private Integer pageNumber;
    private Integer pageSize;
    private Long totalElements;
    private Integer totalPages;
    private boolean lastPage;
    /** Suma de los saldos de todas las facturas pendientes del laboratorio. */
    private BigDecimal totalReceivable;
}
