package marroquinsoftware.labflowapi.model;

/**
 * Cuentas que el código necesita ubicar para generar asientos automáticos
 * (facturas, pagos, gastos). Cada laboratorio tiene a lo sumo una cuenta por
 * clave; el usuario puede renombrarlas o cambiarles el código, pero no
 * desactivarlas ni quitarles la clave.
 */
public enum SystemAccountKey {
    CAJA,
    BANCOS,
    CUENTAS_POR_COBRAR,
    INGRESOS_SERVICIOS,
    DESCUENTOS_VENTAS,
    CAPITAL
}
