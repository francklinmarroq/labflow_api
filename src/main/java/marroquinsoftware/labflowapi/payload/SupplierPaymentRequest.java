package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPaymentRequest {
    @NotNull(message = "Indique la fecha del pago")
    private LocalDate paymentDate;

    @NotNull(message = "Indique el monto del pago")
    @DecimalMin(value = "0.01", message = "El monto del pago debe ser mayor que cero")
    private BigDecimal amount;

    @NotNull(message = "Seleccione la forma de pago")
    private PaymentMethod method;

    private String reference;
}
