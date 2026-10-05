package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Libro de compras del rango: una fila por compra vigente y los totales. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseBookDTO {
    private LocalDate from;
    private LocalDate to;
    private List<PurchaseBookRowDTO> rows;
    private BigDecimal totalExemptBase;
    private BigDecimal totalTaxedBase15;
    private BigDecimal totalTaxedBase18;
    private BigDecimal totalIsv15;
    private BigDecimal totalIsv18;
    private BigDecimal total;
}
