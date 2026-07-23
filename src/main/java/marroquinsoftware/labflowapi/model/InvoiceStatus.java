package marroquinsoftware.labflowapi.model;

/**
 * Estado de cobro de una factura. PENDIENTE: sin pagos; PARCIAL: con abonos
 * pero saldo abierto; PAGADA: saldo en cero; ANULADA: revertida con
 * contra-asiento (las facturas CAI nunca se borran).
 */
public enum InvoiceStatus {

    PENDIENTE("Pendiente"),
    PARCIAL("Pago parcial"),
    PAGADA("Pagada"),
    ANULADA("Anulada");

    private final String label;

    InvoiceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
