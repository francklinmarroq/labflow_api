package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.AgeDiscountKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuoteDTO {
    private Long id;
    private Long quoteNumber;
    /** Null cuando se cotizó a un paciente que todavía no está registrado. */
    private Long customerId;
    private String patientName;
    private Integer patientAgeInDays;
    private Instant quotedAt;
    private String notes;
    private String createdByUsername;
    private AgeDiscountKind discountKind;
    /** Etiqueta lista para mostrar del descuento aplicado (ej. "Tercera edad"). */
    private String discountLabel;
    private BigDecimal discountPercent;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal total;
    private List<QuoteItemDTO> items;
}
