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
    // Identidad del paciente (nationalIdNumber), embebida de solo lectura (igual que
    // customerName/customerSex: se ignora al crear/actualizar). Se lee del mismo
    // Customer ya cargado, sin consulta extra. Permite que el reporte y el sobre de
    // la orden muestren la identidad sin una llamada serial a GET /customers/{id}.
    private String customerNationalId;
    private Instant requestedAt;
    private OrderStatus status;
    private String notes;
    // Médico solicitante (opcional): NOMBRE de quien refiere la orden, de ida y de
    // vuelta. Solo se muestra en el reporte si viene con valor.
    //
    // Es un nombre y no un id a propósito, al revés que la mayoría de las
    // referencias de este DTO: el médico que aún no esté en el catálogo del
    // laboratorio se da de alta solo al guardar la orden, así que quien la levanta lo
    // escribe sin haber pasado antes por ningún catálogo (mismo trato que `tagNames`,
    // y por el mismo motivo). Escribir un nombre que ya existe —con o sin tildes,
    // mayúsculas o espacios de más— reutiliza ese médico en vez de duplicarlo.
    // Vacío o en blanco = la orden no indica médico; no borra nada del catálogo.
    //
    // Al leer viene el nombre VIGENTE del catálogo, no una copia congelada: corregir
    // el nombre del médico corrige también las órdenes ya levantadas.
    private String referringPhysician;

    // Id del médico solicitante, solo lectura (se ignora al crear/actualizar, igual
    // que customerName y publicToken: lo que se escribe es el nombre de arriba).
    // Permite distinguir dos médicos de nombre parecido y agrupar órdenes por médico
    // sin comparar texto.
    private Long referringPhysicianId;
    private LocalDate lmpDate;
    private boolean pregnant;
    private Integer gestationalWeeks;
    private boolean menopausal;
    // Auditoría de la cancelación (solo lectura; la asigna la API al cancelar).
    // Permite mostrar quién/cuándo/por qué en la pestaña de órdenes canceladas.
    private Instant cancelledAt;
    private String cancelledByUsername;
    private String cancellationReason;
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
    // Etiquetas de la orden (convenio, campaña, empresa…). Se leen en `tags`, ya
    // resueltas con su id y color para pintarlas, y se escriben en `tagNames`, con
    // los nombres tal como se escribieron: las que aún no existan en el laboratorio
    // se crean solas al guardar. Esa asimetría es a propósito — quien levanta la
    // orden escribe "IHSS" sin haber pasado antes por el catálogo. Si `tagNames`
    // viene nulo al actualizar, las etiquetas de la orden se dejan como estaban;
    // una lista vacía sí las quita todas.
    private List<OrderTagDTO> tags;
    private List<String> tagNames;
    // ¿Está congelado el conjunto de exámenes de la orden? Solo lectura (se ignora
    // al crear/actualizar, igual que customerName y publicToken): lo calcula la API
    // desde la factura viva de la orden en LabOrderServiceImp.toDTO. Permite al
    // front deshabilitar "Agregar examen"/"Quitar examen" en vez de descubrir el
    // rechazo al hacer clic, sin una segunda consulta por las facturas de la orden.
    // Primitivo a propósito: ausente significa falso para clientes viejos.
    private boolean testsLocked;
}
