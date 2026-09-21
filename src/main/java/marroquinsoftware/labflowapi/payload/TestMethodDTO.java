package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un método (técnica) del perfil de un examen, tal como viaja en el editor
 * unificado ({@link TestFullDTO}) y en el perfil que lee la pantalla de órdenes
 * ({@link TestConfigDTO}).
 *
 * <p>{@code id} nulo = método nuevo, se agrega al perfil. {@code id} presente =
 * método existente; si el nombre cambió, se renombra, y el nombre nuevo llega a
 * todas las órdenes que ya lo indicaban (renombrar es corregir cómo se escribe una
 * técnica, no cambiar cuál se usó).
 *
 * <p>{@code isDefault} marca cuál de los métodos del perfil se le estampa a un
 * examen nuevo al asignarle el perfil. A lo sumo uno; puede no haber ninguno.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestMethodDTO {

    private Long id;

    /** Nombre visible, que es el que se imprime. En blanco no llega a ser método. */
    private String name;

    /** ¿Es el predeterminado del perfil? Nulo se lee como que no. */
    private Boolean isDefault;
}
