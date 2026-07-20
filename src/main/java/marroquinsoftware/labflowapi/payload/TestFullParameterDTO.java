package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.ParameterSection;
import marroquinsoftware.labflowapi.model.ParameterValueType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Una fila de parámetro dentro del editor unificado de examen (ver TestFullDTO).
 *
 * id == null  → parámetro nuevo, se crea con estos campos.
 * id != null  → parámetro existente que se reutiliza; sus campos se actualizan.
 *
 * referenceRanges reemplaza por completo los rangos del parámetro (se sincroniza:
 * se borran los que ya no vengan, se actualizan los que traen id y se crean los
 * que no). chartXValue solo se usa cuando el perfil es una curva.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestFullParameterDTO {
    private Long id;

    private Long unitId;

    @NotBlank(message = "El nombre del parámetro es obligatorio")
    private String name;

    private ParameterSection section;
    private ParameterValueType valueType;

    // Posición del parámetro en el eje X cuando el perfil es una curva (opcional).
    private BigDecimal chartXValue;

    // No se valida en cascada a propósito: el parameterId de cada rango lo asigna
    // el servidor al sincronizarlos (el parámetro puede ser nuevo y aún sin id).
    private List<ReferenceRangeDTO> referenceRanges = new ArrayList<>();
}
