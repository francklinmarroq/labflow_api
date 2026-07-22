package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.AgeDiscountKind;
import marroquinsoftware.labflowapi.model.InvoiceStatus;
import marroquinsoftware.labflowapi.model.SaleCondition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDTO {
    private Long id;
    private String invoiceNumber;

    // Snapshot fiscal para la impresión (CAI, rango autorizado y emisor).
    private String cai;
    private String caiRangeFrom;
    private String caiRangeTo;
    private LocalDate caiExpirationDate;
    private String labName;
    private String labRtn;
    private String labAddress;
    private String labPhone;
    private String labHeadline;
    private String labFooterNote;
    private String labPacNumber;
    private String labRegExonerado;
    private String labRegSag;
    private String labOrdenCompraExenta;

    private Long orderId;
    private Long orderNumber;
    private Long customerId;
    private String customerName;
    /** Null = consumidor final. */
    private String customerRtn;

    private Instant issuedAt;
    private String issuedByUsername;
    private InvoiceStatus status;
    private String statusLabel;
    private SaleCondition saleCondition;
    private String saleConditionLabel;

    private AgeDiscountKind discountKind;
    private String discountLabel;
    private BigDecimal discountPercent;
    /** Bruto, antes de cualquier descuento. */
    private BigDecimal subtotal;
    /** Rebaja acumulada en las líneas (regalías, precios especiales). */
    private BigDecimal itemDiscountAmount;
    /** Rebaja atribuida al tramo de edad. */
    private BigDecimal discountAmount;
    /** Rebaja adicional: promociones o precio de cierre. */
    private BigDecimal otherDiscountAmount;
    private BigDecimal total;
    private BigDecimal paidAmount;
    /** Saldo pendiente (total - pagado); 0 en facturas anuladas. */
    private BigDecimal balance;
    /** Total en letras para la impresión, generado por la API. */
    private String totalInWords;

    private Instant annulledAt;
    private String annulledByUsername;
    private String annulmentReason;

    private List<InvoiceItemDTO> items;
    /** Solo viene en el detalle; null en los listados. */
    private List<PaymentDTO> payments;
}
