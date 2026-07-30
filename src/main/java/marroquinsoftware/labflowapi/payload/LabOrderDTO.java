package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.Sex;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabOrderDTO {
    private Long id;
    private Long orderNumber;
    // Token del enlace público de resultados (solo lectura; lo asigna la API al
    // crear la orden). El front lo usa para armar la URL/QR que se comparte al
    // paciente. Ver LabOrder.publicToken.
    private String publicToken;
    @NotNull(message = "Debe seleccionar un paciente")
    private Long customerId;
    // Nombre del paciente, embebido de solo lectura (se ignora al crear/actualizar;
    // la orden se vincula por customerId). Permite que el listado de órdenes muestre
    // el nombre sin una segunda llamada a /customers. Ver LabOrderServiceImp.toDTO.
    private String customerName;
    // Sexo y edad del paciente, embebidos de solo lectura (igual que customerName:
    // se ignoran al crear/actualizar). Se leen del mismo Customer ya cargado, sin
    // consulta extra. Permiten que el detalle/impresión de la orden elija los rangos
    // de referencia aplicables sin una llamada serial a GET /customers/{id}.
    private Sex customerSex;
    private Integer customerAgeInDays;
    private Instant requestedAt;
    private OrderStatus status;
    private String notes;
    private LocalDate lmpDate;
    private boolean pregnant;
    private Integer gestationalWeeks;
    private boolean menopausal;
    // IDs de los exámenes a incluir al CREAR la orden (solo escritura, opcional; se
    // ignora al leer y al actualizar). Permite crear la orden y todos sus exámenes en
    // una sola llamada, en lugar de un POST /orders + N POST /orders/{id}/tests
    // seriales. Cada request paga el piso de ~0.7 s (navegador → Worker → DO →
    // contenedor), así que colapsar N+1 en 1 ahorra N invocaciones de Cloudflare y
    // toda la cascada serial. Ver LabOrderServiceImp.createOrder. Los exámenes se
    // crean en el MISMO orden recibido (mismos datos que POST /orders/{id}/tests:
    // sin perfil, sin notas, sin tipo de muestra), preservando el comportamiento
    // observable. El endpoint por examen se conserva para agregar exámenes a una
    // orden ya existente.
    private List<Long> testIds;
}
