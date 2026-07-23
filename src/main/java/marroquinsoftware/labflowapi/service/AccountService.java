package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.AccountDTO;
import marroquinsoftware.labflowapi.payload.AccountRequest;
import marroquinsoftware.labflowapi.payload.AccountUpdateRequest;

import java.util.List;

public interface AccountService {

    /** Catálogo de cuentas del laboratorio, ordenado por código. */
    List<AccountDTO> getAccounts(boolean activeOnly);

    AccountDTO createAccount(AccountRequest request);

    /** Edita código y nombre; en cuentas del sistema solo el nombre. El tipo nunca cambia. */
    AccountDTO updateAccount(Long accountId, AccountUpdateRequest request);

    /** Activa o desactiva la cuenta; las del sistema no se desactivan. No hay borrado. */
    AccountDTO setActive(Long accountId, boolean active);
}
