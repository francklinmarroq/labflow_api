package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Cliente de facturación: empresa, aseguradora o titular de convenio. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingClientDTO {
    private Long id;

    @NotBlank(message = "La razón social del cliente es obligatoria")
    private String name;

    @NotBlank(message = "El RTN del cliente es obligatorio")
    private String rtn;

    private String phone;
    private String email;
    private String address;
}
