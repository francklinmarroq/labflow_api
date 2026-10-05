package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.AccountingPeriodDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * Cierre y reapertura de períodos contables. Cerrar traslada los ingresos y
 * gastos del período a "Resultado del ejercicio" con una partida de cierre y
 * bloquea el registro de partidas en ese rango; reabrir revierte esa partida
 * con un contra-asiento, sin borrar nada.
 */
public interface AccountingPeriodService {

    /** Todos los períodos del laboratorio, el más reciente primero. */
    List<AccountingPeriodDTO> getPeriods();

    /**
     * Cierra el período [from, to]. Rechaza el rango si se traslapa con un
     * período cerrado. Sin saldos de ingresos ni gastos, cierra sin partida.
     */
    AccountingPeriodDTO close(LocalDate from, LocalDate to);

    /** Reabre el período cerrado más reciente; rechaza cualquier otro. */
    AccountingPeriodDTO reopen(Long periodId);
}
