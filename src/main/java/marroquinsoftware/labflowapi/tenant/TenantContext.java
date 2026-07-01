package marroquinsoftware.labflowapi.tenant;

/**
 * Guarda el laboratorio (tenant) de la petición en curso.
 * Lo llena {@code AuthTokenFilter} a partir del usuario autenticado y se limpia
 * al terminar la petición para no filtrar el tenant a otro hilo del pool.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_LABORATORY = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setLaboratoryId(Long laboratoryId) {
        CURRENT_LABORATORY.set(laboratoryId);
    }

    public static Long getLaboratoryId() {
        return CURRENT_LABORATORY.get();
    }

    public static void clear() {
        CURRENT_LABORATORY.remove();
    }
}
