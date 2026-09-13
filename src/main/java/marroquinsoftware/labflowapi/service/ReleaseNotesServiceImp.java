package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.User;
import marroquinsoftware.labflowapi.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleaseNotesServiceImp implements ReleaseNotesService {

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public void markSeen(String username, Long laboratoryId, String version) {
        // Una versión vacía o ausente no borra lo guardado. Es la única garantía que
        // la API puede sostener de verdad sobre "el marcador no retrocede": ordenar
        // versiones exigiría interpretarlas, y eso es justo lo que este servicio no
        // hace. Que la versión marcada sea la que corresponde es del cliente, que
        // solo marca el anuncio que acaba de mostrar.
        if (version == null || version.isBlank()) return;

        // Se escribe SOLO sobre la fila (correo, laboratorio) de quien llama: el
        // usuario lo pone la autenticación, no la petición, así que no hay forma de
        // marcar por otra persona.
        userRepository.findByUsernameAndLaboratoryId(username, laboratoryId)
                .ifPresent((User user) -> {
                    user.setLastSeenReleaseVersion(version);
                    userRepository.save(user);
                });
    }
}
