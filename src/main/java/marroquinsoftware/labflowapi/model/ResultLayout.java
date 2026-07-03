package marroquinsoftware.labflowapi.model;

/**
 * Forma en que se listan los resultados de un perfil (TestConfig) en el reporte.
 *
 * STANDARD: tabla ordenada por el orden del perfil y agrupada por seccion del
 *           parametro (comportamiento por defecto).
 * ANTIBIOGRAM: los resultados se agrupan por su valor de sensibilidad (Sensible,
 *           Intermedio, Resistente) en vez de por parametro. Pensado para
 *           antibiogramas, donde cada parametro es un antibiotico y el valor es
 *           la interpretacion de sensibilidad.
 */
public enum ResultLayout {
    STANDARD,
    ANTIBIOGRAM
}
