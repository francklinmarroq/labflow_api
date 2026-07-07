package marroquinsoftware.labflowapi.model;

/**
 * Estado fisiológico al que pertenece un rango de referencia, además de sexo y
 * edad. Sirve para pruebas cuyo valor normal depende de un contexto que no se
 * deriva del expediente (fase del ciclo, gestación, menopausia): el sistema lo
 * computa desde el contexto clínico de la orden (FUM/gestación) y así elige el
 * rango que aplica en vez de imprimir todas las categorías.
 */
public enum ReferenceContextKind {
    /** Rango normal común: aplica solo por sexo/edad (comportamiento actual). */
    NONE,
    /** Fase del ciclo menstrual; la ventana {@code contextMin/Max} es el día del ciclo. */
    CYCLE_PHASE,
    /** Gestación; la ventana {@code contextMin/Max} es la semana gestacional. */
    GESTATION,
    /** Menopausia; aplica cuando la orden marca a la paciente como menopáusica. */
    MENOPAUSE
}
