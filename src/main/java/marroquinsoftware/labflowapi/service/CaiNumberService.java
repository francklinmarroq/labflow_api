package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.Laboratory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Asigna el número fiscal de la factura desde los CAI configurados en el
 * laboratorio (formato hondureño {@code EEE-PPP-TT-NNNNNNNN}).
 *
 * <p>Se intenta primero el CAI 1 y, si está vencido, agotado o sin configurar,
 * se cae automáticamente al CAI 2 (el de respaldo). El correlativo vive en el
 * campo {@code cai*CurrentNumber} del laboratorio: quien llama debe traer la
 * fila del laboratorio con bloqueo de escritura
 * ({@code LaboratoryRepository.findWithLockById}) para que dos emisiones
 * concurrentes no repitan número, y guardar el laboratorio en la misma
 * transacción.
 */
@Service
public class CaiNumberService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Número emitido con el snapshot del CAI usado, para congelarlo en la factura. */
    public record IssuedCaiNumber(String invoiceNumber, String cai, String rangeFrom,
                                  String rangeTo, LocalDate expirationDate) {}

    /**
     * Emite el siguiente número y deja el correlativo avanzado en la entidad
     * (el caller persiste el laboratorio). Si ningún CAI está vigente y con
     * números disponibles, lanza {@code APIException} explicando cada CAI.
     */
    public IssuedCaiNumber next(Laboratory laboratory) {
        List<String> reasons = new ArrayList<>();

        Optional<IssuedCaiNumber> first = tryIssue(laboratory, 1, reasons);
        if (first.isPresent()) return first.get();

        Optional<IssuedCaiNumber> second = tryIssue(laboratory, 2, reasons);
        if (second.isPresent()) return second.get();

        throw new APIException("No hay un CAI vigente con números disponibles ("
                + String.join("; ", reasons)
                + "). Actualice los datos de facturación en la configuración del laboratorio.");
    }

    private Optional<IssuedCaiNumber> tryIssue(Laboratory lab, int slot, List<String> reasons) {
        String cai = slot == 1 ? lab.getCai1() : lab.getCai2();
        LocalDate expiration = slot == 1 ? lab.getCai1ExpirationDate() : lab.getCai2ExpirationDate();
        String rangeFrom = slot == 1 ? lab.getCai1RangeFrom() : lab.getCai2RangeFrom();
        String rangeTo = slot == 1 ? lab.getCai1RangeTo() : lab.getCai2RangeTo();
        String current = slot == 1 ? lab.getCai1CurrentNumber() : lab.getCai2CurrentNumber();

        if (isBlank(cai)) {
            reasons.add("CAI " + slot + ": sin configurar");
            return Optional.empty();
        }
        if (expiration == null) {
            reasons.add("CAI " + slot + ": sin fecha límite de emisión");
            return Optional.empty();
        }
        if (expiration.isBefore(LocalDate.now())) {
            reasons.add("CAI " + slot + ": venció el " + DATE.format(expiration));
            return Optional.empty();
        }
        if (isBlank(rangeFrom) || isBlank(rangeTo)) {
            reasons.add("CAI " + slot + ": sin rango autorizado");
            return Optional.empty();
        }

        long fromNumber = parseCorrelative(rangeFrom, slot);
        long toNumber = parseCorrelative(rangeTo, slot);
        long currentNumber = isBlank(current) ? fromNumber : parseCorrelative(current, slot);
        if (currentNumber > toNumber) {
            reasons.add("CAI " + slot + ": rango agotado");
            return Optional.empty();
        }

        String prefix = prefixOf(rangeFrom, slot);
        String invoiceNumber = prefix + "-" + String.format("%08d", currentNumber);
        // Se deja escrito el siguiente número completo (legible en la pantalla
        // de configuración del laboratorio).
        String nextNumber = prefix + "-" + String.format("%08d", currentNumber + 1);
        if (slot == 1) lab.setCai1CurrentNumber(nextNumber);
        else lab.setCai2CurrentNumber(nextNumber);

        return Optional.of(new IssuedCaiNumber(invoiceNumber, cai.trim(), rangeFrom.trim(),
                rangeTo.trim(), expiration));
    }

    /** Último grupo del número (el correlativo de 8 dígitos). */
    private long parseCorrelative(String value, int slot) {
        String[] groups = value.trim().split("-");
        String last = groups[groups.length - 1].trim();
        try {
            return Long.parseLong(last);
        } catch (NumberFormatException e) {
            throw new APIException("Revise la configuración del CAI " + slot
                    + ": los números del rango autorizado deben tener el formato 000-001-01-00000001.");
        }
    }

    /** Los tres primeros grupos (establecimiento-punto de emisión-tipo de documento). */
    private String prefixOf(String rangeFrom, int slot) {
        String[] groups = rangeFrom.trim().split("-");
        if (groups.length != 4) {
            throw new APIException("Revise la configuración del CAI " + slot
                    + ": el rango autorizado debe tener el formato 000-001-01-00000001.");
        }
        return groups[0].trim() + "-" + groups[1].trim() + "-" + groups[2].trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
