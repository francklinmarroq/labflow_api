package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Convierte el total de la factura a letras, como exige el formato impreso:
 * {@code toLempiras(1234.56)} → "UN MIL DOSCIENTOS TREINTA Y CUATRO LEMPIRAS
 * CON 56/100". Vive en la API para que el texto salga igual en cualquier
 * cliente.
 */
@Component
public class AmountInWordsConverter {

    private static final String[] UNITS = {
            "", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
            "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE",
            "DIECIOCHO", "DIECINUEVE", "VEINTE", "VEINTIUNO", "VEINTIDOS", "VEINTITRES",
            "VEINTICUATRO", "VEINTICINCO", "VEINTISEIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"
    };

    private static final String[] TENS = {
            "", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"
    };

    private static final String[] HUNDREDS = {
            "", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS",
            "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"
    };

    public String toLempiras(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        if (amount.signum() < 0) {
            throw new APIException("No se puede escribir en letras un monto negativo.");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        long integerPart = normalized.longValue();
        int cents = normalized.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        if (integerPart > 999_999_999L) {
            throw new APIException("El monto es demasiado grande para escribirlo en letras.");
        }

        String words;
        if (integerPart == 0) {
            words = "CERO LEMPIRAS";
        } else if (integerPart == 1) {
            words = "UN LEMPIRA";
        } else if (integerPart % 1_000_000 == 0) {
            // Millones exactos llevan "DE": "UN MILLON DE LEMPIRAS".
            words = integerToWords(integerPart) + " DE LEMPIRAS";
        } else {
            // "UNO" se apocopa también antes de la moneda: "VEINTIUN LEMPIRAS".
            words = apocope(integerToWords(integerPart)) + " LEMPIRAS";
        }
        return words + " CON " + String.format("%02d", cents) + "/100";
    }

    private String integerToWords(long number) {
        StringBuilder out = new StringBuilder();

        long millions = number / 1_000_000;
        long thousands = (number % 1_000_000) / 1_000;
        long units = number % 1_000;

        if (millions > 0) {
            out.append(millions == 1 ? "UN MILLON" : apocope(hundredsToWords((int) millions)) + " MILLONES");
        }
        if (thousands > 0) {
            if (out.length() > 0) out.append(' ');
            // En facturas se acostumbra "UN MIL" en vez del "MIL" a secas.
            out.append(apocope(hundredsToWords((int) thousands))).append(" MIL");
        }
        if (units > 0) {
            if (out.length() > 0) out.append(' ');
            out.append(hundredsToWords((int) units));
        }
        return out.toString();
    }

    /** Grupo de hasta tres cifras (1-999). */
    private String hundredsToWords(int number) {
        if (number == 100) return "CIEN";
        StringBuilder out = new StringBuilder();
        int hundreds = number / 100;
        int rest = number % 100;
        if (hundreds > 0) out.append(HUNDREDS[hundreds]);
        if (rest > 0) {
            if (out.length() > 0) out.append(' ');
            if (rest < 30) {
                out.append(UNITS[rest]);
            } else {
                out.append(TENS[rest / 10]);
                if (rest % 10 > 0) out.append(" Y ").append(UNITS[rest % 10]);
            }
        }
        return out.toString();
    }

    /** "UNO" se apocopa antes de MIL/MILLONES: "VEINTIUN MIL", "UN MIL". */
    private String apocope(String words) {
        if (words.endsWith("UNO")) return words.substring(0, words.length() - 1);
        return words;
    }
}
