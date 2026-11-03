package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.AgeDiscountKind;
import marroquinsoftware.labflowapi.model.QuoteStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    /** Nombre de quien elaboró (o el correo si no tiene nombre). */
    private String createdByName;
    private AgeDiscountKind discountKind;
    /** Etiqueta lista para mostrar del descuento aplicado (ej. "Tercera edad"). */
    private String discountLabel;
    private BigDecimal discountPercent;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal total;
    private List<QuoteItemDTO> items;

    /** Estado calculado al momento de la consulta (hora de Honduras). */
    private QuoteStatus status;
    /** Etiqueta lista para mostrar del estado (ej. "Vigente"). */
    private String statusLabel;
    private Integer validityDays;
    /** Último día en que se respetan los precios cotizados. */
    private LocalDate validUntil;
    /** Orden creada a partir de la cotización (si está convertida). */
    private Long orderId;
    private Long orderNumber;
    /** Cotización que la reemplazó (si está recotizada). */
    private Long requotedById;
    private Long requotedByNumber;
    /** Cotización vencida de la que salió esta (si es una recotización). */
    private Long requotedFromId;
    private Long requotedFromNumber;
    private Instant updatedAt;
    private String updatedByUsername;
    /** Nombre de quien la modificó por última vez (o el correo si no tiene nombre). */
    private String updatedByName;
}
