package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.Sex;
import marroquinsoftware.labflowapi.model.TestArea;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Todo lo que la página pública necesita para renderizar el reporte de una orden,
 * en una sola respuesta. Es un endpoint SIN autenticación, así que solo se exponen
 * los campos que el reporte muestra: nada de precios/costos, datos fiscales del
 * laboratorio ni contacto del paciente. Cuando la orden aún no está lista
 * (verificada/entregada) se devuelve {@code ready=false} con el mínimo para
 * mostrar un mensaje con la identidad del laboratorio.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicReportDTO {

    /** true si la orden está verificada o entregada; con false el resto va vacío. */
    private boolean ready;

    private Lab laboratory;
    private Patient patient;
    private Order order;

    private List<LabTestDTO> tests;      // exámenes de la orden (LabTest)
    private List<TestDef> testDefs;      // catálogo de exámenes referenciados
    private List<ParameterDTO> parameters;
    private List<UnitDTO> units;
    private List<TestConfigDTO> configs;

    // Corridas por id de LabTest y rangos aplicables por parámetro. Jackson
    // serializa las llaves Long como texto; el front reconstruye los Map.
    private Map<Long, List<TestRunDTO>> runsByLabTest;
    private Map<Long, List<ReferenceRangeDTO>> rangesByParameter;

    /** Membrete del laboratorio (sin datos fiscales ni de facturación). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Lab {
        private String name;
        private String address1;
        private String address2;
        private String phone;
        private String email;
        private String rtn;
        private String logoUrl; // URL firmada con vencimiento (bucket privado)
    }

    /** Datos del paciente que salen en el reporte (sin teléfono/correo/fiscales). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Patient {
        private String name;
        private Sex sex;
        private Integer ageInDays;
        private String nationalIdNumber;
    }

    /** Datos de la orden necesarios para el reporte y el contexto clínico. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Order {
        private Long orderNumber;
        private Instant requestedAt;
        private OrderStatus status;
        private String notes;
        private LocalDate lmpDate;
        private boolean pregnant;
        private Integer gestationalWeeks;
        private boolean menopausal;
    }

    /** Examen del catálogo, sin precio ni costo. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestDef {
        private Long id;
        private String name;
        private TestArea area;
    }
}
