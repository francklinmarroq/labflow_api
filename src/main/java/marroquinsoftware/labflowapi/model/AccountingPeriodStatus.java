package marroquinsoftware.labflowapi.model;

/** Estado de un período contable: cerrado, o cerrado y luego reabierto. */
public enum AccountingPeriodStatus {

    CLOSED("Cerrado"),
    REOPENED("Reabierto");

    private final String label;

    AccountingPeriodStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
