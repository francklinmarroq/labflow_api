package marroquinsoftware.labflowapi.exceptions;

import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor
public class ResourceNotFoundException extends RuntimeException {

    // Traduce el nombre técnico de la entidad al término que usa el usuario en
    // pantalla, para que el mensaje de "no encontrado" sea entendible.
    private static final Map<String, String> RESOURCE_LABELS = Map.ofEntries(
            Map.entry("Customer", "el paciente"),
            Map.entry("LabOrder", "la orden"),
            Map.entry("LabTest", "el examen de la orden"),
            Map.entry("Test", "el examen"),
            Map.entry("TestConfig", "el perfil de examen"),
            Map.entry("TestRun", "la corrida de resultados"),
            Map.entry("TestResult", "el resultado"),
            Map.entry("Parameter", "el parámetro"),
            Map.entry("Unit", "la unidad"),
            Map.entry("Pathology", "la patología"),
            Map.entry("AgeRange", "el rango de edad"),
            Map.entry("ReferenceRange", "el rango de referencia"),
            Map.entry("Referral", "la remisión"),
            Map.entry("Role", "el rol"),
            Map.entry("User", "el usuario"),
            Map.entry("Laboratory", "el laboratorio"),
            Map.entry("Invitation", "la invitación")
    );

    String resourceName;
    String field;
    String fieldName;
    Long fieldId;

    public ResourceNotFoundException(String message, String resourceName, String field, String fieldName) {
        super(buildMessage(resourceName, field, fieldName));
        this.resourceName = resourceName;
        this.field = field;
        this.fieldName = fieldName;
    }

    public ResourceNotFoundException(String resourceName, String field, Long fieldId) {
        super(buildMessage(resourceName, field, String.valueOf(fieldId)));
        this.resourceName = resourceName;
        this.field = field;
        this.fieldId = fieldId;
    }

    private static String buildMessage(String resourceName, String field, String value) {
        String label = RESOURCE_LABELS.getOrDefault(resourceName, resourceName);
        return String.format("No se encontró %s (%s: %s). "
                + "Es posible que otro usuario lo haya eliminado; recargue la página.",
                label, field, value);
    }
}
