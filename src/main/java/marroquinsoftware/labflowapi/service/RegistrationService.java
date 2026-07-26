package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.model.Role;
import marroquinsoftware.labflowapi.model.User;
import marroquinsoftware.labflowapi.payload.RegisterRequest;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.repositories.UserRepository;
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


    /**
     * Crea, en una sola transacción, el laboratorio (tenant) y su usuario dueño.
     * Si algo falla, no queda ni el lab ni el usuario a medias.
     */
    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new APIException("Este correo ya está registrado. Use otro correo o inicie sesión.");
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

        // El catálogo por defecto NO se siembra aquí a propósito: esta petición es
        // anónima, así que la sesión de Hibernate queda fijada a NO_TENANT y el
        // sembrado caería bajo el tenant equivocado. El laboratorio nace con
        // seedStatus = PENDING; la primera vez que el dueño entra autenticado, la app
        // le ofrece cargar los datos de inicio y el sembrado corre con el tenant
        // correcto (ver LaboratoryService#chooseStarterData).
        return user;
    }
}
