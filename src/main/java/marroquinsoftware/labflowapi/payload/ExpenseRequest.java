package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
public class ExpenseRequest {

    @NotNull(message = "Indique la fecha del gasto")
    private LocalDate expenseDate;

    @NotBlank(message = "Escriba la descripción del gasto")
    private String description;

    @NotNull(message = "Indique el monto del gasto")
    @DecimalMin(value = "0.01", message = "El monto del gasto debe ser mayor que cero")
    private BigDecimal amount;

    @NotNull(message = "Seleccione la cuenta de gasto")
    private Long expenseAccountId;

    @NotNull(message = "Seleccione la forma de pago")
    private PaymentMethod method;
}
