package marroquinsoftware.labflowapi.model;

/**
 * Estado de una compra, derivado de su condición y de lo pagado: una compra de
 * contado nace PAGADA; una a crédito pasa de PENDIENTE a PARCIAL y a PAGADA con
 * los pagos al proveedor.
 */
public enum PurchaseStatus {

    PENDIENTE("Pendiente"),
    PARCIAL("Pago parcial"),
    PAGADA("Pagada"),
    ANULADA("Anulada");

    private final String label;

    PurchaseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
