package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.TestFullDTO;

/**
 * Orquesta el alta/edición de un examen completo (examen + su perfil + parámetros
 * con sus rangos) en una sola operación atómica, para la pantalla unificada.
 */
public interface TestBuilderService {

    TestFullDTO getFull(Long testId);

    TestFullDTO createFull(TestFullDTO dto);

    TestFullDTO updateFull(TestFullDTO dto, Long testId);
}
