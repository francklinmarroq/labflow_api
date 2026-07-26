package marroquinsoftware.labflowapi.model;

/**
 * Estado de la decisión de "datos de inicio" de un laboratorio.
 *
 * <p>Al registrarse, el laboratorio nace {@link #PENDING}: el catálogo por
 * defecto NO se siembra en el registro (correría con el tenant equivocado). La
 * primera vez que el dueño entra autenticado, la app le ofrece cargar los datos
 * de inicio y, según su elección, el laboratorio pasa a {@link #ACCEPTED}
 * (se sembró el catálogo) o {@link #DECLINED} (empieza vacío). Cualquiera de los
 * dos estados finales significa "ya decidió" y no se vuelve a preguntar.
 */
public enum OnboardingSeedStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
