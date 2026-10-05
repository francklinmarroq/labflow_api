package marroquinsoftware.labflowapi.model;

/** A nombre de quién se emite una factura. */
public enum InvoiceRecipientType {
    /** Un paciente del padrón. */
    PATIENT,
    /** Un cliente de facturación (empresa o aseguradora). */
    BILLING_CLIENT,
    /** Consumidor final: un nombre escrito, sin ficha. */
    FINAL_CONSUMER
}
