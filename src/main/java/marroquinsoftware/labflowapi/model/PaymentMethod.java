package marroquinsoftware.labflowapi.model;

/**
 * Forma en la que entra o sale el dinero. Determina la cuenta contable del
 * movimiento: EFECTIVO va contra Caja; TARJETA y TRANSFERENCIA, contra Bancos.
 */
public enum PaymentMethod {

    EFECTIVO("Efectivo"),
    TARJETA("Tarjeta"),
    TRANSFERENCIA("Transferencia");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
