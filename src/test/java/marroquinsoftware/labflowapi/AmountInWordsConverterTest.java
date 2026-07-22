package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.service.AmountInWordsConverter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** El total en letras que se imprime en la factura sale de aquí, tal cual. */
class AmountInWordsConverterTest {

    private final AmountInWordsConverter converter = new AmountInWordsConverter();

    private String words(String value) {
        return converter.toLempiras(new BigDecimal(value));
    }

    @Test
    void writesRepresentativeAmounts() {
        assertEquals("CERO LEMPIRAS CON 00/100", words("0.00"));
        assertEquals("UN LEMPIRA CON 00/100", words("1.00"));
        assertEquals("VEINTIUN LEMPIRAS CON 50/100", words("21.50"));
        assertEquals("CIEN LEMPIRAS CON 00/100", words("100"));
        assertEquals("CIENTO CINCUENTA LEMPIRAS CON 75/100", words("150.75"));
        assertEquals("QUINIENTOS LEMPIRAS CON 00/100", words("500"));
        assertEquals("UN MIL DOSCIENTOS TREINTA Y CUATRO LEMPIRAS CON 56/100", words("1234.56"));
        assertEquals("VEINTIUN MIL LEMPIRAS CON 00/100", words("21000"));
        assertEquals("UN MILLON DE LEMPIRAS CON 00/100", words("1000000"));
        assertEquals("DOS MILLONES TRESCIENTOS CUARENTA Y CINCO MIL SEISCIENTOS SETENTA Y OCHO LEMPIRAS CON 90/100",
                words("2345678.90"));
    }

    @Test
    void roundsCentsHalfUp() {
        assertEquals("DIEZ LEMPIRAS CON 56/100", words("10.555"));
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThrows(APIException.class, () -> words("-1.00"));
    }
}
