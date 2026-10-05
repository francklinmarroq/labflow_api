package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.PayablesAgingDTO;
import marroquinsoftware.labflowapi.payload.PurchaseBookDTO;
import marroquinsoftware.labflowapi.payload.SupplierStatementDTO;

import java.time.LocalDate;

/**
 * Reportes de compras y cuentas por pagar. Se calculan sobre los documentos
 * (compras y pagos), no sobre el mayor, porque son los que tienen el desglose
 * fiscal y el proveedor.
 */
public interface PurchaseReportService {

    /** Libro de compras: las compras vigentes del rango con su desglose fiscal. */
    PurchaseBookDTO getPurchaseBook(LocalDate from, LocalDate to);

    /** Estado de cuenta de un proveedor: compras a crédito y pagos vigentes del rango. */
    SupplierStatementDTO getSupplierStatement(Long supplierId, LocalDate from, LocalDate to);

    /** Antigüedad de saldos por pagar a la fecha de corte, por proveedor. */
    PayablesAgingDTO getPayablesAging(LocalDate date);
}
