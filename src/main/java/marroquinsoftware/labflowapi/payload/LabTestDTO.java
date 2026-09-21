package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabTestDTO {
    private Long id;
    private Long orderId;

    @NotNull(message = "Debe seleccionar un examen")
    private Long testId;

    private Long testConfigId;

    private String notes;

    private String sampleType;

    /**
     * Método/técnica del examen, por NOMBRE, tanto al escribir como al leer.
     *
     * <p>Es un nombre y no un id a propósito: los métodos son del PERFIL del examen
     * y nacen solos: escribir acá un nombre que el perfil no tenga lo agrega al
     * perfil y lo usa, sin ninguna alta previa, y además lo deja como el
     * predeterminado del perfil — de modo que los siguientes exámenes de ese perfil
     * arrancan ya con él. Un cliente escrito antes de que los métodos vivieran en el
     * perfil sigue funcionando igual, que es lo que permite desplegar el API antes
     * que el frontend.
     *
     * <p>Lo que se lee es el nombre VIGENTE del método en el perfil: por eso
     * corregirlo ahí corrige también las órdenes ya levantadas y sus reportes.
     * Vacío o nulo al escribir deja el examen sin método, sin tocar el perfil ni su
     * predeterminado.
     */
    private String method;

    /**
     * Identificador del método dentro del perfil. Solo lectura: se IGNORA si viene
     * en una escritura — lo que se escribe es el nombre. Está para que un cliente
     * sepa cuál de los métodos del perfil es este sin comparar texto.
     */
    private Long methodId;
}
