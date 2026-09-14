package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Lo que un cliente de facturación debe hoy: cuántas facturas con saldo abierto
 * tiene y cuánto suman esos saldos. Es una lista de trabajo para cobrar, no un
 * documento fiscal, así que el nombre es el vigente del catálogo y no el que se
 * congeló en cada factura.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingClientBalanceDTO {
    private Long billingClientId;
    private String billingClientName;
    /** Facturas PENDIENTE o PARCIAL de este cliente. */
    private Long invoiceCount;
    /** Suma de los saldos (total - pagado) de esas facturas. */
    private BigDecimal balance;
}
