package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.PaymentMethod;

import java.math.BigDecimal;

/** Abono a una factura (también se usa como pago inicial al emitirla). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull(message = "Indique el monto del pago")
    @DecimalMin(value = "0.01", message = "El monto del pago debe ser mayor que cero")
    private BigDecimal amount;

    @NotNull(message = "Seleccione la forma de pago")
    private PaymentMethod method;

    /** Referencia del voucher o número de transferencia, si aplica. */
    private String reference;
}
