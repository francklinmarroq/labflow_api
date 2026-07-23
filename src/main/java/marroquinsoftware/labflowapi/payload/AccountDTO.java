package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import marroquinsoftware.labflowapi.model.AccountType;
import marroquinsoftware.labflowapi.model.SystemAccountKey;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountDTO {
    private Long id;
    private String code;
    private String name;
    private AccountType type;
    /** Etiqueta lista para mostrar del tipo (ej. "Activo"). */
    private String typeLabel;
    /** No nulo en cuentas del sistema: no se desactivan ni cambian de código. */
    private SystemAccountKey systemKey;
    private boolean active;
}
