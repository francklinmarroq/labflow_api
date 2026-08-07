package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.TestDTO;
import marroquinsoftware.labflowapi.payload.TestFullDTO;

/**
 * Orquesta el alta/edición de un examen completo (examen + su perfil + parámetros
 * con sus rangos) en una sola operación atómica, para la pantalla unificada.
 */
public interface TestBuilderService {

    TestFullDTO getFull(Long testId);

    TestFullDTO createFull(TestFullDTO dto);

    TestFullDTO updateFull(TestFullDTO dto, Long testId);

    /**
     * Elimina el examen completo: su perfil y los parámetros/rangos que solo usa
     * ese examen (los compartidos con otro perfil se conservan). Se bloquea si el
     * examen está usado en alguna orden.
     */
    TestDTO deleteFull(Long testId);
}
