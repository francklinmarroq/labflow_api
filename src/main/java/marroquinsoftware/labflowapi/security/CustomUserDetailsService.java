package marroquinsoftware.labflowapi.security;

import marroquinsoftware.labflowapi.model.User;
import marroquinsoftware.labflowapi.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Carga por username para el chequeo de contraseña del login. Como un mismo
     * correo puede tener varias filas (una por laboratorio) que comparten el hash
     * de contraseña, basta cualquiera para validar credenciales; el laboratorio
     * concreto se decide después (selector) y el JWT se emite ya con su labId.
     * La resolución por request NO pasa por aquí: AuthTokenFilter carga la fila
     * exacta por (username, labId) del token.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Se prefiere una fila habilitada (hash de contraseña real); si el correo
        // solo tiene invitaciones pendientes, cae a cualquiera para que Spring
        // responda con el DisabledException correspondiente.
        User user = userRepository.findFirstByUsernameAndEnabledTrueOrderById(username)
                .or(() -> userRepository.findFirstByUsernameOrderById(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return new AppUserDetails(user);
    }
}
