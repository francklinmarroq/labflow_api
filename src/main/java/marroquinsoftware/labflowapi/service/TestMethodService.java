package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.TestConfig;
import marroquinsoftware.labflowapi.model.TestMethod;
import marroquinsoftware.labflowapi.payload.TestMethodDTO;

import java.util.List;

/**
 * Los métodos (técnicas) del perfil de un examen. Todo pasa por acá porque el
 * "a lo sumo un predeterminado por perfil" no es una restricción que la base
 * pueda sostener: la sostiene este servicio, que es el único que la escribe.
 */
public interface TestMethodService {

    /**
     * Traduce el nombre escrito en una orden a un método de ESE perfil, creándolo
     * si el perfil aún no lo tiene. Es el "se guarda la primera vez que se use":
     * quien atiende escribe "ELISA" y el método queda en el perfil, disponible para
     * los siguientes exámenes sin haber pasado por el editor del examen.
     *
     * <p>Busca solo dentro del perfil recibido: el mismo nombre bajo otro perfil es
     * otro método. Devuelve {@code null} si el nombre viene vacío o en blanco — el
     * examen simplemente no indica método — y en ese caso no crea nada.
     */
    TestMethod resolveOrCreate(TestConfig testConfig, String name);

    /**
     * Deja este método como el predeterminado de su perfil, desmarcando al que lo
     * fuera antes, de modo que el "a lo sumo uno" se cumple sin que quien llama
     * tenga que desmarcar nada.
     */
    void markAsDefault(TestMethod method);

    /**
     * Deja los métodos del perfil exactamente como los manda el editor del examen:
     * agrega los nuevos, renombra los que cambiaron, marca el predeterminado y quita
     * los que ya no vengan.
     *
     * <p>Quitar uno que algún examen de alguna orden indique se RECHAZA con un
     * {@code APIException} que dice cuántos son: el método está impreso en reportes
     * ya entregados y borrarlo no es una corrección sino perder el dato de con qué
     * se produjo ese resultado. Quitar el que era el predeterminado deja el perfil
     * sin predeterminado, sin promover a ningún otro.
     *
     * <p>El mismo método escrito dos veces en un mismo guardado es uno solo, no un
     * error. Renombrar uno al nombre de otro que el perfil conserva sí se rechaza:
     * fusionaría dos técnicas distintas en los reportes que las nombran.
     */
    void reconcile(TestConfig testConfig, List<TestMethodDTO> incoming);

    /** Los métodos del perfil, en el orden en que se ofrecen, con cuál es el predeterminado. */
    List<TestMethodDTO> toDTOs(TestConfig testConfig);
}
