package marroquinsoftware.labflowapi.model;

/**
 * Papel en que el laboratorio imprime sus facturas. La API solo lo guarda y lo
 * valida; el diseño de cada tamaño es del frontend.
 */
public enum InvoicePaperSize {

    /** A5 apaisado (210×148 mm): dos facturas por A4 cortada a la mitad. */
    A5_LANDSCAPE,
    /** Rollo térmico de 80 mm. */
    THERMAL_80,
    /** Rollo térmico de 58 mm. */
    THERMAL_58;

    /** El valor guardado, o A5 si viene nulo o no se reconoce. */
    public static InvoicePaperSize orDefault(String value) {
        if (value == null) return A5_LANDSCAPE;
        for (InvoicePaperSize size : values()) {
            if (size.name().equals(value)) return size;
        }
        return A5_LANDSCAPE;
    }

    public static boolean isValid(String value) {
        for (InvoicePaperSize size : values()) {
            if (size.name().equals(value)) return true;
        }
        return false;
    }
}
