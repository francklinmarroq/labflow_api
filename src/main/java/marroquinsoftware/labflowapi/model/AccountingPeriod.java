package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Período contable cerrado (o cerrado y luego reabierto). Al cerrarse, sus
 * ingresos y gastos se trasladan a "Resultado del ejercicio" con una partida de
 * cierre, y el diario rechaza partidas con fecha dentro del rango. Reabrirlo no
 * lo borra: revierte la partida de cierre con un contra-asiento y lo deja como
 * {@link AccountingPeriodStatus#REOPENED}, de modo que el historial de cierres
 * queda completo.
 */
@Entity
// Sin restricción única por rango: un período reabierto y vuelto a cerrar deja
// dos filas con el mismo rango (una REOPENED, otra CLOSED), y las dos son
// historia. Que no haya dos CLOSED traslapados lo valida el servicio al cerrar.
@Table(name = "accounting_periods", indexes = @Index(
        name = "ix_accounting_periods_range", columnList = "laboratory_id, start_date, end_date"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountingPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountingPeriodStatus status;

    /** Partida que trasladó el resultado; null si el período se cerró sin movimientos. */
    @ManyToOne
    @JoinColumn(name = "closing_entry_id")
    private JournalEntry closingEntry;

    /** Contra-asiento de la partida de cierre; null mientras el período siga cerrado. */
    @ManyToOne
    @JoinColumn(name = "reversal_entry_id")
    private JournalEntry reversalEntry;

    /** Utilidad (positivo) o pérdida (negativo) trasladada a "Resultado del ejercicio". */
    @Column(name = "result_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal resultAmount;

    private Instant closedAt;
    private String closedByUsername;

    private Instant reopenedAt;
    private String reopenedByUsername;
}
