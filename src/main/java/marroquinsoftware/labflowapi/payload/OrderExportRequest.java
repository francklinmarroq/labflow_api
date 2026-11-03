package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Órdenes a exportar a Excel: las que la pantalla de Órdenes tiene filtradas.
 * El filtro vive en el cliente, así que se mandan sus ids tal cual.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderExportRequest {

    @NotEmpty(message = "Indique al menos una orden para exportar.")
    @Size(max = 5000, message = "Se pueden exportar como máximo 5000 órdenes a la vez.")
    private List<@NotNull(message = "El id de la orden no puede ser nulo.") Long> orderIds;
}
