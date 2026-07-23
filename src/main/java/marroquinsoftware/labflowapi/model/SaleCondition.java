package marroquinsoftware.labflowapi.model;

/**
 * Condición de venta impresa en la factura. CONTADO exige el pago completo al
 * emitir; CREDITO deja saldo por cobrar (con abono inicial opcional).
 */
public enum SaleCondition {

    CONTADO("Contado"),
    CREDITO("Crédito");

    private final String label;

    SaleCondition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
