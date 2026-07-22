package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Account;
import marroquinsoftware.labflowapi.payload.AccountDTO;
import marroquinsoftware.labflowapi.payload.AccountRequest;
import marroquinsoftware.labflowapi.payload.AccountUpdateRequest;
import marroquinsoftware.labflowapi.repositories.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountServiceImp implements AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountSeeder accountSeeder;

    @Override
    @Transactional
    public List<AccountDTO> getAccounts(boolean activeOnly) {
        // Laboratorios anteriores al módulo contable reciben su catálogo aquí,
        // la primera vez que abren cualquier pantalla de contabilidad.
        accountSeeder.seedDefaultAccounts();
        Sort sort = Sort.by("code").ascending();
        List<Account> accounts = activeOnly
                ? accountRepository.findByActiveTrue(sort)
                : accountRepository.findAll(sort);
        return accounts.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional
    public AccountDTO createAccount(AccountRequest request) {
        String code = request.getCode().trim();
        if (accountRepository.existsByCode(code)) {
            throw new APIException("Ya existe una cuenta con el código " + code + ".");
        }
        Account account = new Account();
        account.setCode(code);
        account.setName(request.getName().trim());
        account.setType(request.getType());
        account.setActive(true);
        return toDTO(accountRepository.save(account));
    }

    @Override
    @Transactional
    public AccountDTO updateAccount(Long accountId, AccountUpdateRequest request) {
        Account account = findAccount(accountId);
        String code = request.getCode().trim();
        if (account.getSystemKey() != null && !account.getCode().equals(code)) {
            throw new APIException("El código de una cuenta del sistema no se puede cambiar.");
        }
        if (!account.getCode().equals(code) && accountRepository.existsByCodeAndIdNot(code, accountId)) {
            throw new APIException("Ya existe una cuenta con el código " + code + ".");
        }
        account.setCode(code);
        account.setName(request.getName().trim());
        return toDTO(accountRepository.save(account));
    }

    @Override
    @Transactional
    public AccountDTO setActive(Long accountId, boolean active) {
        Account account = findAccount(accountId);
        if (!active && account.getSystemKey() != null) {
            throw new APIException("Las cuentas del sistema no se pueden desactivar.");
        }
        account.setActive(active);
        return toDTO(accountRepository.save(account));
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "accountId", accountId));
    }

    private AccountDTO toDTO(Account account) {
        return new AccountDTO(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getType(),
                account.getType().getLabel(),
                account.getSystemKey(),
                account.isActive());
    }
}
