package marroquinsoftware.labflowapi.service;

/**
 * Marcador de novedades por usuario: recuerda cuál fue el último anuncio que se le
 * mostró, para que el frontend no se lo repita en cada carga.
 *
 * <p>La API no entiende de versiones. Guarda la cadena que le mandan y la devuelve
 * igual; quién decide si toca anunciar es el cliente, que es donde vive el contenido.
 */
public interface ReleaseNotesService {

    /**
     * Marca como vista la versión indicada para la membresía (correo, laboratorio)
     * del usuario en sesión.
     *
     * <p>Una versión vacía o ausente se IGNORA: el marcador nunca se limpia, porque
     * volver al estado "no ha visto nada" le repetiría a la persona un anuncio que ya
     * cerró. Ignorar no es error — la llamada se considera exitosa igual.
     *
     * @param username     correo del usuario en sesión
     * @param laboratoryId laboratorio activo de la sesión; el marcador es por
     *                     membresía, no por correo (ver design.md)
     * @param version      identificador opaco de la versión anunciada
     */
    void markSeen(String username, Long laboratoryId, String version);
}
