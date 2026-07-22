package marroquinsoftware.labflowapi.model;

/**
 * Descuento por edad que aplica a una cotización. Los umbrales de edad y los
 * porcentajes los configura cada laboratorio en {@link Laboratory}; aquí solo
 * se identifica cuál de los dos tramos cayó.
 */
public enum AgeDiscountKind {

    /** Sin descuento: la edad no llega al umbral, o el laboratorio no lo configuró. */
    NONE("Sin descuento"),

    /** Tercera edad. */
    THIRD_AGE("Tercera edad"),

    /** Cuarta edad (tramo mayor; tiene prioridad sobre la tercera edad). */
    FOURTH_AGE("Cuarta edad");

    private final String label;

    AgeDiscountKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
