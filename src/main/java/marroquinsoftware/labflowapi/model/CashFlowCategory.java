package marroquinsoftware.labflowapi.model;

import java.util.EnumSet;

/**
 * Clasificación de un movimiento de Caja o Bancos en el flujo de efectivo,
 * según el origen de su partida. Es una aproximación: un pago de planilla
 * registrado como gasto cae en {@link #GASTOS}.
 */
public enum CashFlowCategory {

    COBROS_CLIENTES("Cobros a clientes"),
    PAGOS_REMISIONES("Pagos de remisiones"),
    GASTOS("Gastos"),
    PARTIDAS_MANUALES("Partidas manuales"),
    OTROS("Otros movimientos");

    private final String label;

    CashFlowCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Anulaciones de facturas y pagos, y cualquier origen nuevo, caen en {@link #OTROS}. */
    public static CashFlowCategory of(JournalSourceType sourceType) {
        if (sourceType == JournalSourceType.PAGO) return COBROS_CLIENTES;
        if (EnumSet.of(JournalSourceType.REMISION, JournalSourceType.ANULACION_REMISION).contains(sourceType)) {
            return PAGOS_REMISIONES;
        }
        if (EnumSet.of(JournalSourceType.GASTO, JournalSourceType.ANULACION_GASTO).contains(sourceType)) {
            return GASTOS;
        }
        if (sourceType == JournalSourceType.MANUAL) return PARTIDAS_MANUALES;
        return OTROS;
    }
}
