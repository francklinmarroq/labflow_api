package marroquinsoftware.labflowapi.payload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.ChartType;
import marroquinsoftware.labflowapi.model.ResultLayout;
import marroquinsoftware.labflowapi.model.TestArea;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Agregado del "editor unificado de examen": un examen (Test), su único perfil
 * (TestConfig) y sus parámetros con rangos, todo en un mismo payload para crearlo
 * o editarlo en una sola pantalla y de forma atómica.
 *
 * En el dominio un examen podría tener varios perfiles, pero la UI unificada
 * asume 1 examen = 1 perfil (ningún dato actual lo viola).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestFullDTO {
    // --- Examen ---
    private Long id;

    @NotNull(message = "El nombre del examen es obligatorio")
    private String name;

    private BigDecimal price;
    private BigDecimal cost;
    private TestArea area;

    // --- Perfil (TestConfig) ---
    private Long testConfigId;

    // Nombre del perfil; si viene vacío se usa el nombre del examen.
    private String profileName;

    private boolean active;
    private ChartType chartType;
    private ResultLayout resultLayout;
    private String chartXAxisLabel;

    // --- Parámetros del perfil, en el orden del reporte ---
    @NotEmpty(message = "Debe agregar al menos un parámetro")
    @Valid
    private List<TestFullParameterDTO> parameters = new ArrayList<>();
}
