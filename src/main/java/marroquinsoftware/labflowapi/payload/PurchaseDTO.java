package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.PaymentMethod;
import marroquinsoftware.labflowapi.model.PurchaseStatus;
import marroquinsoftware.labflowapi.model.SaleCondition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Compra con su desglose fiscal. En el listado lines y payments vienen vacíos;
 * el detalle los trae junto con la partida que generó.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseDTO {
    private Long id;
    private Long supplierId;
    private String supplierName;
    private String supplierRtn;
    private LocalDate purchaseDate;
    private String fiscalNumber;
    private String cai;
    private LocalDate caiDeadline;
    private SaleCondition condition;
    private String conditionLabel;
    private PaymentMethod method;
    private String methodLabel;
    private String notes;
    private BigDecimal exemptBase;
    private BigDecimal taxedBase15;
    private BigDecimal taxedBase18;
    private BigDecimal isv15;
    private BigDecimal isv18;
    private BigDecimal total;
    private BigDecimal paidAmount;
    private BigDecimal balance;
    private PurchaseStatus status;
    private String statusLabel;
    private List<PurchaseLineDTO> lines;
    private List<SupplierPaymentDTO> payments;
    /** Partida de la compra; null en el listado. */
    private Long journalEntryId;
    private Long journalEntryNumber;
    private Instant createdAt;
    private String createdByUsername;
    private boolean annulled;
    private Instant annulledAt;
    private String annulledByUsername;
    private String annulmentReason;
}
