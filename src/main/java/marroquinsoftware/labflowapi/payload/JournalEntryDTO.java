package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.JournalSourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryDTO {
    private Long id;
    private Long entryNumber;
    private LocalDate entryDate;
    private String description;
    private JournalSourceType sourceType;
    /** Etiqueta lista para mostrar del origen (ej. "Partida manual"). */
    private String sourceTypeLabel;
    /** Id del documento origen (factura, pago o gasto); null en partidas manuales. */
    private Long sourceId;
    private Instant createdAt;
    private String createdByUsername;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private List<JournalLineDTO> lines;
}
