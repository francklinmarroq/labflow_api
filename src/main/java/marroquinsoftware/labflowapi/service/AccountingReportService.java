package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.LedgerReportDTO;
import marroquinsoftware.labflowapi.payload.TrialBalanceDTO;

import java.time.LocalDate;

public interface AccountingReportService {

    /** Mayor de una cuenta: saldo inicial, movimientos del rango y saldo final. */
    LedgerReportDTO getLedger(Long accountId, LocalDate from, LocalDate to);

    /** Balanza de comprobación del rango: sumas y saldos por cuenta con movimientos. */
    TrialBalanceDTO getTrialBalance(LocalDate from, LocalDate to);
}
