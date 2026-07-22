package marroquinsoftware.labflowapi.model;

/**
 * Origen de una partida del libro diario: qué evento la generó. Las partidas
 * automáticas guardan además el id del documento origen en
 * {@code JournalEntry.sourceId}.
 */
public enum JournalSourceType {

    FACTURA("Factura"),
    PAGO("Pago"),
    GASTO("Gasto"),
    ANULACION_FACTURA("Anulación de factura"),
    ANULACION_PAGO("Anulación de pago"),
    ANULACION_GASTO("Anulación de gasto"),
    MANUAL("Partida manual");

    private final String label;

    JournalSourceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
