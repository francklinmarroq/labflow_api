package marroquinsoftware.labflowapi.model;

/**
 * Naturaleza de una cuenta contable. Define de qué lado aumenta su saldo:
 * ACTIVO y GASTO son de naturaleza deudora; PASIVO, CAPITAL e INGRESO,
 * acreedora.
 */
public enum AccountType {

    ACTIVO("Activo", true),
    PASIVO("Pasivo", false),
    CAPITAL("Capital", false),
    INGRESO("Ingresos", false),
    GASTO("Gastos", true);

    private final String label;

    /** true si la cuenta aumenta por el debe (naturaleza deudora). */
    private final boolean debitNature;

    AccountType(String label, boolean debitNature) {
        this.label = label;
        this.debitNature = debitNature;
    }

    public String getLabel() {
        return label;
    }

    public boolean isDebitNature() {
        return debitNature;
    }
}
