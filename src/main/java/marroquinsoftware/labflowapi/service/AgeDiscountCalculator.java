package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.AgeDiscountKind;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.payload.AgeDiscountDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Regla del descuento por edad, compartida entre cotizaciones y facturas para
 * que ambas cobren exactamente lo mismo. Los umbrales y porcentajes viven en
 * {@link Laboratory}.
 */
@Component
public class AgeDiscountCalculator {

    /** Misma conversión que usa el frontend para mostrar la edad del paciente. */
    private static final int DAYS_PER_YEAR = 365;

    /**
     * Elige el tramo de descuento por edad. La cuarta edad tiene prioridad sobre
     * la tercera: si el paciente llega a ambos umbrales, se le aplica el de
     * cuarta edad. Un tramo sin umbral o sin porcentaje configurado no aplica.
     */
    public AgeDiscountDTO discountFor(Integer ageInDays, Laboratory laboratory) {
        AgeDiscountDTO none = new AgeDiscountDTO(AgeDiscountKind.NONE, AgeDiscountKind.NONE.getLabel(), BigDecimal.ZERO);
        if (ageInDays == null || ageInDays <= 0) return none;
        int years = ageInDays / DAYS_PER_YEAR;

        if (qualifies(years, laboratory.getFourthAgeMinYears(), laboratory.getFourthAgeDiscountPercent())) {
            return new AgeDiscountDTO(AgeDiscountKind.FOURTH_AGE, AgeDiscountKind.FOURTH_AGE.getLabel(),
                    laboratory.getFourthAgeDiscountPercent());
        }
        if (qualifies(years, laboratory.getThirdAgeMinYears(), laboratory.getThirdAgeDiscountPercent())) {
            return new AgeDiscountDTO(AgeDiscountKind.THIRD_AGE, AgeDiscountKind.THIRD_AGE.getLabel(),
                    laboratory.getThirdAgeDiscountPercent());
        }
        return none;
    }

    private boolean qualifies(int years, Integer minYears, BigDecimal percent) {
        return minYears != null
                && percent != null
                && percent.compareTo(BigDecimal.ZERO) > 0
                && years >= minYears;
    }
}
