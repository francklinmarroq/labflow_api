package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.Role;
import marroquinsoftware.labflowapi.model.User;
import marroquinsoftware.labflowapi.payload.RegisterRequest;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.UserRepository;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private CatalogSeeder catalogSeeder;

    /**
     * Crea, en una sola transacción, el laboratorio (tenant) y su usuario dueño.
     * Si algo falla, no queda ni el lab ni el usuario a medias.
     */
    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new APIException("Username already taken");
        }

        Laboratory laboratory = modelMapper.map(request.getLaboratory(), Laboratory.class);
        laboratory.setId(null);
        laboratory = laboratoryRepository.save(laboratory);

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(bCryptPasswordEncoder.encode(request.getPassword()));
        user.setEnabled(true);
        user.setRole(Role.OWNER);
        user.setLaboratory(laboratory);
        user = userRepository.save(user);

        // El usuario recién creado es el dueño del laboratorio.
        laboratory.setOwner(user);
        laboratoryRepository.save(laboratory);

        // Siembra el catálogo por defecto para el laboratorio nuevo. Se fija el
        // TenantContext al lab recién creado para que las entradas del catálogo
        // reciban su laboratory_id (@TenantId) automáticamente; se restaura al final.
        Long previousTenant = TenantContext.getLaboratoryId();
        TenantContext.setLaboratoryId(laboratory.getId());
        try {
            catalogSeeder.seedDefaultCatalog();
        } finally {
            if (previousTenant != null) {
                TenantContext.setLaboratoryId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }

        return user;
    }
}
