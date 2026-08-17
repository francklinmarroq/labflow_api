package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTagDTO {
    private Long id;

    @NotBlank(message = "Escriba el nombre de la etiqueta")
    @Size(max = 60, message = "El nombre de la etiqueta no puede pasar de 60 caracteres")
    private String name;

    /** Color del distintivo en hexadecimal ({@code #1d4ed8}); opcional. */
    private String color;

    /**
     * Cuántas órdenes la usan. Solo se llena en el listado del catálogo (de ahí
     * que sea nulo en las etiquetas embebidas dentro de una orden o factura), para
     * poder ver qué tan usada está antes de renombrarla o borrarla.
     */
    private Long orderCount;
}
