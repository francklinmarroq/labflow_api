package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferringPhysicianDTO {
    private Long id;

    @NotBlank(message = "Escriba el nombre del médico")
    @Size(max = 150, message = "El nombre del médico no puede pasar de 150 caracteres")
    private String name;

    /**
     * Cuántas órdenes lo tienen como médico solicitante. Solo se llena en el listado
     * del catálogo, para poder ver qué tan usado está antes de corregirle el nombre
     * o borrarlo.
     */
    private Long orderCount;
}
