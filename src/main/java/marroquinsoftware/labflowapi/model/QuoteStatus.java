package marroquinsoftware.labflowapi.model;

import marroquinsoftware.labflowapi.config.AppConstants;

import java.time.LocalDate;

/**
 * Estado de una {@link Quote}. No se guarda en la base: se deriva de sus vínculos
 * y de su fecha de vencimiento, así nunca se desincroniza ni hace falta una tarea
 * que "venza" cotizaciones.
 */
public enum QuoteStatus {

    /** Vigente: se puede editar y convertir en orden. */
    OPEN("Vigente"),

    /** Pasó su fecha "válida hasta" sin convertirse; solo se puede recotizar. */
    EXPIRED("Vencida"),

    /** Cerrada: ya se creó una orden a partir de ella. */
    CONVERTED("Convertida"),

    /** Cerrada: se recotizó y la reemplazó una cotización nueva. */
    REQUOTED("Recotizada");

    private final String label;

    QuoteStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Estado de la cotización hoy, en hora de Honduras. */
    public static QuoteStatus of(Quote quote) {
        return of(quote, LocalDate.now(AppConstants.LAB_ZONE));
    }

    /**
     * Los cerrados ganan sobre la fecha: una cotización convertida sigue siendo
     * convertida aunque ya haya pasado su vigencia. Es vigente hasta el final del
     * día {@code validUntil}.
     */
    public static QuoteStatus of(Quote quote, LocalDate today) {
        if (quote.getOrderId() != null) return CONVERTED;
        if (quote.getRequotedById() != null) return REQUOTED;
        if (quote.getValidUntil() != null && today.isAfter(quote.getValidUntil())) return EXPIRED;
        return OPEN;
    }
}
