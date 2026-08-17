package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "lab_orders", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_order_number_per_lab",
        columnNames = {"laboratory_id", "order_number"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Correlativo visible de la orden, único por laboratorio (folio 1, 2, 3…).
     * A diferencia del {@code id}, se asigna desde un contador transaccional
     * ({@link marroquinsoftware.labflowapi.model.LabOrderCounter}) para que no
     * queden huecos cuando una creación falla y hace rollback.
     */
    @Column(name = "order_number")
    private Long orderNumber;

    /**
     * Token opaco (UUID) para el enlace público de resultados. Con él, el paciente
     * abre su reporte sin sesión ({@code /api/v1/public/orders/{token}} y la página
     * pública del front). Es un capability de portador: sirve para armar la URL y el
     * QR que se imprime en el reporte, por eso se guarda en claro y no se hashea.
     * Se asigna una sola vez al crear la orden y no cambia.
     */
    @Column(name = "public_token", unique = true, updatable = false, length = 36)
    private String publicToken;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String notes;

    // Médico solicitante (opcional): nombre de quien refiere la orden. Solo se
    // captura si se llena y solo se imprime en el reporte cuando tiene valor.
    @Column(name = "referring_physician", length = 150)
    private String referringPhysician;

    // Contexto clínico de la visita, capturado una vez, del que se computa el día
    // del ciclo / semana gestacional para elegir el rango de referencia que aplica
    // en pruebas por fase (progesterona, FSH, LH, gestación…). Solo relevante para
    // pacientes de sexo femenino.
    private LocalDate lmpDate; // fecha de última menstruación (FUM)
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean pregnant;
    private Integer gestationalWeeks; // override si no hay FUM
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean menopausal;

    // Auditoría de la cancelación (borrado lógico a CANCELLED), espejo de los
    // campos annulled* de Invoice: quién la canceló, cuándo y por qué. Nulos
    // mientras la orden esté activa.
    private Instant cancelledAt;
    private String cancelledByUsername;
    private String cancellationReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<LabTest> tests;

    /**
     * Etiquetas con las que se clasifica la orden (convenios, campañas, empresas).
     * Sin cascade a propósito: la etiqueta vive en el catálogo del laboratorio y
     * se reutiliza entre órdenes; borrar o editar la orden no debe tocarla. Lo que
     * se crea o se borra aquí son las filas de la tabla de unión.
     *
     * <p>El listado devuelve cada orden con sus etiquetas; sin el @BatchSize eso
     * sería una consulta por orden al recorrer la página (igual que InvoiceItem).
     */
    @ManyToMany
    @JoinTable(name = "lab_order_tags",
            joinColumns = @JoinColumn(name = "order_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @BatchSize(size = 50)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<OrderTag> tags = new LinkedHashSet<>();
}
