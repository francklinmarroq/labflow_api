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
    CUENTAS_POR_PAGAR,
    INGRESOS_SERVICIOS,
    DESCUENTOS_VENTAS,
    EXAMENES_REMITIDOS,
    CAPITAL,
    /** Recibe el traslado de ingresos y gastos al cerrar un período contable. */
    RESULTADO_DEL_EJERCICIO,
    /** ISV pagado en compras: hoy es costo porque el laboratorio vende exento. */
    ISV_NO_RECUPERABLE_COMPRAS
}
