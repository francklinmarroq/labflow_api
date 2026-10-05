package marroquinsoftware.labflowapi.payload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.PaymentMethod;
import marroquinsoftware.labflowapi.model.SaleCondition;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequest {
    @NotNull(message = "Seleccione el proveedor")
    private Long supplierId;

    @NotNull(message = "Indique la fecha de la compra")
    private LocalDate purchaseDate;

    @NotBlank(message = "Escriba el número del documento del proveedor")
    private String fiscalNumber;

    private String cai;
    private LocalDate caiDeadline;

    @NotNull(message = "Indique si la compra es de contado o a crédito")
    private SaleCondition condition;

    /** Obligatorio en las de contado; se ignora en las de crédito. */
    private PaymentMethod method;

    private String notes;

    /** La lista vacía la rechaza el servicio con su propio mensaje. */
    @Valid
    private List<PurchaseLineRequest> lines;
}
