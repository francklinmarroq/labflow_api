package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.InvoiceRecipientType;

/**
 * A nombre de quién se emite la factura. Según {@code type} manda
 * {@code customerId} (paciente), {@code billingClientId} (empresa) o
 * {@code name} (consumidor final). {@code rtn} aplica al paciente y al
 * consumidor final; el de una empresa sale siempre de su ficha.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRecipientDTO {
    private InvoiceRecipientType type;
    private Long customerId;
    private Long billingClientId;
    private String name;
    private String rtn;
}
