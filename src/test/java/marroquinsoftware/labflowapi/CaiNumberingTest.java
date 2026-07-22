package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.service.CaiNumberService;
import marroquinsoftware.labflowapi.service.CaiNumberService.IssuedCaiNumber;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Numeración fiscal CAI: secuencia sin huecos dentro del rango autorizado,
 * caída automática al CAI de respaldo y errores accionables cuando la
 * configuración no da para emitir.
 */
class CaiNumberingTest {

    private final CaiNumberService service = new CaiNumberService();

    private Laboratory labWithCai1(String rangeFrom, String rangeTo, String current, LocalDate expiration) {
        Laboratory lab = new Laboratory();
        lab.setCai1("254F86-612421-9701AB-016921-3E7CD1-35");
        lab.setCai1ExpirationDate(expiration);
        lab.setCai1RangeFrom(rangeFrom);
        lab.setCai1RangeTo(rangeTo);
        lab.setCai1CurrentNumber(current);
        return lab;
    }

    @Test
    void issuesSequentialNumbersAndAdvancesTheCounter() {
        Laboratory lab = labWithCai1("000-001-01-00000001", "000-001-01-00000100", null,
                LocalDate.now().plusMonths(6));

        IssuedCaiNumber first = service.next(lab);
        assertEquals("000-001-01-00000001", first.invoiceNumber(), "sin correlativo guardado arranca en rangeFrom");
        assertEquals("000-001-01-00000002", lab.getCai1CurrentNumber(), "deja el siguiente número escrito");

        IssuedCaiNumber second = service.next(lab);
        assertEquals("000-001-01-00000002", second.invoiceNumber());
        assertEquals(lab.getCai1(), second.cai(), "el snapshot lleva el CAI usado");
    }

    @Test
    void acceptsBareCorrelativeInCurrentNumber() {
        Laboratory lab = labWithCai1("000-001-01-00000001", "000-001-01-00000100", "42",
                LocalDate.now().plusMonths(6));
        assertEquals("000-001-01-00000042", service.next(lab).invoiceNumber());
    }

    @Test
    void fallsBackToCai2WhenCai1IsExhausted() {
        Laboratory lab = labWithCai1("000-001-01-00000001", "000-001-01-00000010",
                "000-001-01-00000011", LocalDate.now().plusMonths(6));
        lab.setCai2("AAAA11-222222-3333BB-444444-5C6DE1-99");
        lab.setCai2ExpirationDate(LocalDate.now().plusYears(1));
        lab.setCai2RangeFrom("000-001-01-00000101");
        lab.setCai2RangeTo("000-001-01-00000200");

        IssuedCaiNumber issued = service.next(lab);
        assertEquals("000-001-01-00000101", issued.invoiceNumber());
        assertEquals(lab.getCai2(), issued.cai());
        assertEquals("000-001-01-00000102", lab.getCai2CurrentNumber());
    }

    @Test
    void fallsBackToCai2WhenCai1IsExpiredEvenWithNumbersLeft() {
        Laboratory lab = labWithCai1("000-001-01-00000001", "000-001-01-00000100",
                "000-001-01-00000050", LocalDate.now().minusDays(1));
        lab.setCai2("AAAA11-222222-3333BB-444444-5C6DE1-99");
        lab.setCai2ExpirationDate(LocalDate.now().plusYears(1));
        lab.setCai2RangeFrom("000-001-01-00000101");
        lab.setCai2RangeTo("000-001-01-00000200");

        assertEquals(lab.getCai2(), service.next(lab).cai());
    }

    @Test
    void usesTheLastNumberOfTheRange() {
        Laboratory lab = labWithCai1("000-001-01-00000001", "000-001-01-00000010",
                "000-001-01-00000010", LocalDate.now().plusMonths(6));
        assertEquals("000-001-01-00000010", service.next(lab).invoiceNumber(), "el último número del rango es válido");
    }

    @Test
    void failsWithActionableMessageWhenNoCaiIsUsable() {
        // CAI 1 agotado, CAI 2 sin configurar.
        Laboratory lab = labWithCai1("000-001-01-00000001", "000-001-01-00000010",
                "000-001-01-00000011", LocalDate.now().plusMonths(6));

        APIException ex = assertThrows(APIException.class, () -> service.next(lab));
        assertTrue(ex.getMessage().contains("CAI 1: rango agotado"), ex.getMessage());
        assertTrue(ex.getMessage().contains("CAI 2: sin configurar"), ex.getMessage());
        assertTrue(ex.getMessage().contains("configuración del laboratorio"), ex.getMessage());
    }

    @Test
    void failsWhenBothCaisAreExpired() {
        Laboratory lab = labWithCai1("000-001-01-00000001", "000-001-01-00000100",
                null, LocalDate.now().minusDays(3));
        lab.setCai2("AAAA11-222222-3333BB-444444-5C6DE1-99");
        lab.setCai2ExpirationDate(LocalDate.now().minusDays(1));
        lab.setCai2RangeFrom("000-001-01-00000101");
        lab.setCai2RangeTo("000-001-01-00000200");

        APIException ex = assertThrows(APIException.class, () -> service.next(lab));
        assertTrue(ex.getMessage().contains("venció"), ex.getMessage());
    }

    @Test
    void failsWithClearMessageOnMalformedRange() {
        Laboratory lab = labWithCai1("000-001-01-ABCDEFGH", "000-001-01-00000100", null,
                LocalDate.now().plusMonths(6));

        APIException ex = assertThrows(APIException.class, () -> service.next(lab));
        assertTrue(ex.getMessage().contains("formato"), ex.getMessage());
    }

    @Test
    void nothingIsConsumedWhenIssuingFails() {
        Laboratory lab = labWithCai1("000-001-01-00000001", "000-001-01-00000010",
                "000-001-01-00000011", LocalDate.now().plusMonths(6));
        String before = lab.getCai1CurrentNumber();
        assertThrows(APIException.class, () -> service.next(lab));
        assertEquals(before, lab.getCai1CurrentNumber(), "un intento fallido no debe mover el correlativo");
    }
}
