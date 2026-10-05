package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.Invoice;
import marroquinsoftware.labflowapi.model.InvoiceOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Órdenes incluidas en facturas. Todo lo que pregunta "¿esta orden está
 * facturada?" pasa por aquí, para que valga igual con una orden por factura o
 * con varias.
 */
public interface InvoiceOrderRepository extends JpaRepository<InvoiceOrder, Long> {

    /**
     * Facturas vigentes (no anuladas) que incluyen la orden, la más reciente
     * primero. En la práctica hay a lo sumo una: emitir valida que ninguna orden
     * tenga ya factura vigente, bajo el lock del laboratorio.
     */
    @Query("""
            select io.invoice from InvoiceOrder io
            where io.order.id = :orderId
              and io.invoice.status <> marroquinsoftware.labflowapi.model.InvoiceStatus.ANULADA
            order by io.invoice.issuedAt desc
            """)
    List<Invoice> findLiveInvoicesOfOrder(@Param("orderId") Long orderId);

    default Optional<Invoice> findLiveInvoiceOfOrder(Long orderId) {
        return findLiveInvoicesOfOrder(orderId).stream().findFirst();
    }

    /**
     * De las órdenes indicadas, cuáles tienen factura vigente. Resuelve el bloqueo
     * de exámenes de una página entera de órdenes en UNA consulta: preguntarlo por
     * orden dentro de toDTO sería una consulta por fila del listado.
     */
    @Query("""
            select distinct io.order.id from InvoiceOrder io
            where io.order.id in :orderIds
              and io.invoice.status <> marroquinsoftware.labflowapi.model.InvoiceStatus.ANULADA
            """)
    List<Long> findOrderIdsWithLiveInvoice(@Param("orderIds") Collection<Long> orderIds);

    long countByInvoiceId(Long invoiceId);

    /**
     * Filas [invoiceId, orderId, orderNumber, agePercent] de las órdenes de las
     * facturas emitidas en el rango (anuladas incluidas), para el registro de
     * ventas: los números de orden de cada fila y el porcentaje de edad con que
     * se reparte el descuento.
     */
    @Query("""
            select io.invoice.id, io.order.id, io.order.orderNumber, io.agePercent
            from InvoiceOrder io
            where io.invoice.issuedAt >= :from and io.invoice.issuedAt < :to
            order by io.invoice.id asc, io.order.orderNumber asc
            """)
    List<Object[]> registerOrderRows(@Param("from") Instant from, @Param("to") Instant to);
}
