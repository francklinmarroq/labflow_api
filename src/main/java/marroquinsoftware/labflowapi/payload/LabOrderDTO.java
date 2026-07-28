package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.Sex;

import java.time.Instant;
import java.time.LocalDate;

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
}
