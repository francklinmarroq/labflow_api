package marroquinsoftware.labflowapi.model;

/**
 * Forma en que se presenta un perfil (TestConfig) en el reporte.
 *
 * NONE: tabla normal de parametros (comportamiento por defecto).
 * LINE: ademas de la tabla, los parametros se grafican como una curva (ej.
 *       curva de tolerancia a la glucosa o de insulina), usando el chartXValue
 *       de cada parametro como eje X.
 */
public enum ChartType {
    NONE,
    LINE
}
