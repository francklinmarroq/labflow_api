package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.BalanceSheetDTO;
import marroquinsoftware.labflowapi.payload.CashFlowDTO;
import marroquinsoftware.labflowapi.payload.IncomeStatementDTO;
import marroquinsoftware.labflowapi.payload.LedgerReportDTO;
import marroquinsoftware.labflowapi.payload.TrialBalanceDTO;

import java.time.LocalDate;

public interface AccountingReportService {

    /** Mayor de una cuenta: saldo inicial, movimientos del rango y saldo final. */
    LedgerReportDTO getLedger(Long accountId, LocalDate from, LocalDate to);

    /** Balanza de comprobación del rango: sumas y saldos por cuenta con movimientos. */
    TrialBalanceDTO getTrialBalance(LocalDate from, LocalDate to);

    /** Estado de resultados del rango: ingresos y gastos, sin partidas de cierre. */
    IncomeStatementDTO getIncomeStatement(LocalDate from, LocalDate to);

    /** Balance general a la fecha de corte, con el resultado no trasladado dentro del capital. */
    BalanceSheetDTO getBalanceSheet(LocalDate date);

    /** Flujo de efectivo del rango sobre Caja y Bancos, clasificado por origen. */
    CashFlowDTO getCashFlow(LocalDate from, LocalDate to);
}
