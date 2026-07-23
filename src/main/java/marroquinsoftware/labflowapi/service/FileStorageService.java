package marroquinsoftware.labflowapi.service;

import java.time.Duration;

/**
 * Guarda archivos en el bucket privado (Cloudflare R2, hablado por el API de S3).
 *
 * El bucket no es público: nada se sirve por URL directa. Lo que se guarda en la
 * base de datos es la <em>llave</em> del objeto y cada vez que hay que mostrarlo
 * se firma una URL temporal con {@link #signedUrl(String, Duration)}.
 */
public interface FileStorageService {

    /** True si hay credenciales de R2 configuradas. Sin esto, subir falla. */
    boolean isEnabled();

    /**
     * Sube el archivo y devuelve la llave con la que quedó guardado.
     *
     * @param key         ruta dentro del bucket, ej. {@code laboratorios/3-lab-san-jose/logo-abc.png}
     * @param content     bytes del archivo
     * @param contentType tipo MIME, para que el navegador lo muestre bien al descargarlo
     */
    String upload(String key, byte[] content, String contentType);

    /** URL temporal de lectura para la llave dada. Devuelve null si la llave es null. */
    String signedUrl(String key, Duration ttl);

    /** Borra el objeto. No falla si la llave ya no existe. */
    void delete(String key);
}
