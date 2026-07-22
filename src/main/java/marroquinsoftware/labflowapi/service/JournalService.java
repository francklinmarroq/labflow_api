package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.Account;
import marroquinsoftware.labflowapi.model.JournalEntry;
import marroquinsoftware.labflowapi.model.JournalSourceType;
import marroquinsoftware.labflowapi.model.PaymentMethod;
import marroquinsoftware.labflowapi.model.SystemAccountKey;
import marroquinsoftware.labflowapi.payload.JournalEntryDTO;
import marroquinsoftware.labflowapi.payload.JournalEntryRequest;
import marroquinsoftware.labflowapi.payload.JournalEntryResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Motor de partida doble. Todos los asientos —automáticos y manuales— entran
 * por {@link #post}, que garantiza que la partida cuadre antes de guardarla.
 */
public interface JournalService {

    /**
     * Línea planificada de una partida: la cuenta y su cargo o abono. Usar las
     * fábricas {@link #debit} / {@link #credit} para que el otro monto quede en
     * cero explícitamente.
     */
    record LinePlan(Account account, BigDecimal debit, BigDecimal credit) {

        public static LinePlan debit(Account account, BigDecimal amount) {
            return new LinePlan(account, amount, BigDecimal.ZERO);
        }

        public static LinePlan credit(Account account, BigDecimal amount) {
            return new LinePlan(account, BigDecimal.ZERO, amount);
        }
    }

    /**
     * Crea una partida validada: al menos dos líneas, cada una con débito o
     * crédito (no ambos), cuentas activas y suma de débitos igual a la de
     * créditos. Lanza {@code APIException} si algo no cuadra.
     */
    JournalEntry post(LocalDate date, String description, JournalSourceType sourceType,
                      Long sourceId, List<LinePlan> lines);

    /**
     * Crea el contra-asiento de una partida (débitos y créditos intercambiados)
     * con fecha de hoy. Se usa al anular facturas, pagos y gastos.
     */
    JournalEntry reverse(JournalEntry original, JournalSourceType sourceType,
                         Long sourceId, String description);

    /** Cuenta del sistema del laboratorio actual; siembra el catálogo si aún no existe. */
    Account systemAccount(SystemAccountKey key);

    /** Caja para efectivo; Bancos para tarjeta y transferencia. */
    Account cashOrBank(PaymentMethod method);

    /** Partida original de un documento, para armar su contra-asiento al anular. */
    JournalEntry findSourceEntry(JournalSourceType sourceType, Long sourceId);

    JournalEntryResponse getEntries(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                    LocalDate from, LocalDate to, JournalSourceType sourceType);

    JournalEntryDTO getEntry(Long entryId);

    /** Partida manual del usuario; mismas validaciones que las automáticas. */
    JournalEntryDTO createManualEntry(JournalEntryRequest request);
}
