package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.Account;
import marroquinsoftware.labflowapi.model.AccountType;
import marroquinsoftware.labflowapi.model.SystemAccountKey;
import marroquinsoftware.labflowapi.repositories.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Siembra el catálogo de cuentas contables por defecto de un laboratorio.
 *
 * <p>Se invoca desde {@link RegistrationService} con el {@code TenantContext} ya
 * apuntando al laboratorio nuevo (igual que {@link CatalogSeeder}), y también de
 * forma perezosa desde el motor contable para laboratorios registrados antes de
 * que existiera el módulo de contabilidad.
 *
 * <p>El catálogo default vive en el recurso {@code default-accounts.json}. Las
 * cuentas con {@code system_key} son las que los asientos automáticos necesitan
 * ubicar; ver {@link SystemAccountKey}.
 */
@Service
public class AccountSeeder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountSeeder.class);
    private static final String ACCOUNTS_RESOURCE = "default-accounts.json";

    @Autowired private AccountRepository accountRepository;
    @Autowired private ObjectMapper objectMapper;

    /**
     * Inserta el catálogo de cuentas por defecto en el laboratorio (tenant)
     * actual. Idempotente: si el laboratorio ya tiene cuentas, no hace nada.
     */
    public void seedDefaultAccounts() {
        if (accountRepository.count() > 0) {
            LOGGER.info("AccountSeeder: el laboratorio ya tiene catálogo de cuentas; no se sembró nada.");
            return;
        }

        for (AccountRow row : readAccounts().accounts()) {
            Account account = new Account();
            account.setCode(row.code());
            account.setName(row.name());
            account.setType(AccountType.valueOf(row.type()));
            account.setSystemKey(row.system_key() != null ? SystemAccountKey.valueOf(row.system_key()) : null);
            account.setActive(true);
            accountRepository.save(account);
        }
        LOGGER.info("AccountSeeder: catálogo de cuentas por defecto sembrado.");
    }

    /**
     * Garantiza que exista la cuenta de sistema {@code key} en el laboratorio
     * actual, sin re-sembrar todo el catálogo. Para llaves agregadas después del
     * catálogo inicial (p. ej. las de remisiones): laboratorios ya sembrados no
     * las tienen y {@link #seedDefaultAccounts()} no hace nada porque ya hay
     * cuentas. Si una cuenta con el mismo código ya existe pero sin llave (caso
     * de "Cuentas por pagar", que existía sin system_key), se le asigna la llave
     * en vez de duplicarla.
     */
    public Account ensureSystemAccount(SystemAccountKey key) {
        Account existing = accountRepository.findBySystemKey(key).orElse(null);
        if (existing != null) return existing;

        AccountRow row = readAccounts().accounts().stream()
                .filter(r -> key.name().equals(r.system_key()))
                .findFirst()
                .orElseThrow(() -> new APIException(
                        "No hay definición por defecto para la cuenta del sistema " + key + "."));

        Account byCode = accountRepository.findByCode(row.code()).orElse(null);
        if (byCode != null) {
            if (byCode.getSystemKey() == null) {
                byCode.setSystemKey(key);
                return accountRepository.save(byCode);
            }
            return byCode;
        }

        Account account = new Account();
        account.setCode(row.code());
        account.setName(row.name());
        account.setType(AccountType.valueOf(row.type()));
        account.setSystemKey(key);
        account.setActive(true);
        return accountRepository.save(account);
    }

    private Accounts readAccounts() {
        ClassPathResource resource = new ClassPathResource(ACCOUNTS_RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, Accounts.class);
        } catch (IOException e) {
            throw new APIException("No se pudo leer el catálogo de cuentas por defecto: " + e.getMessage());
        }
    }

    // Nombres de campo en snake_case idénticos al JSON, como en CatalogSeeder.
    record Accounts(List<AccountRow> accounts) {}

    record AccountRow(String code, String name, String type, String system_key) {}
}
