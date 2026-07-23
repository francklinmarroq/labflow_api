package marroquinsoftware.labflowapi.service;

import jakarta.annotation.PreDestroy;
import marroquinsoftware.labflowapi.exceptions.APIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

/**
 * Implementación sobre Cloudflare R2. R2 expone un API compatible con S3, así que
 * se usa el SDK de AWS apuntando al endpoint de la cuenta.
 *
 * Ojo con dos detalles propios de R2:
 * <ul>
 *   <li>La región siempre es {@code auto}.</li>
 *   <li>Hay que usar path-style ({@code endpoint/bucket/llave}); R2 no resuelve
 *       el bucket como subdominio.</li>
 * </ul>
 *
 * El cliente se crea perezosamente: si no hay credenciales (tests, desarrollo
 * local sin bucket) la app arranca igual y solo falla si alguien intenta subir.
 */
@Service
public class R2FileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(R2FileStorageService.class);

    private final String endpoint;
    private final String bucket;
    private final String accessKeyId;
    private final String secretAccessKey;

    private volatile S3Client client;
    private volatile S3Presigner presigner;

    public R2FileStorageService(
            @Value("${app.r2.endpoint:}") String endpoint,
            @Value("${app.r2.bucket:}") String bucket,
            @Value("${app.r2.accessKeyId:}") String accessKeyId,
            @Value("${app.r2.secretAccessKey:}") String secretAccessKey) {
        // El endpoint que da Cloudflare a veces viene con el bucket pegado al
        // final (…​.r2.cloudflarestorage.com/labflow-files). El SDK necesita solo
        // el host de la cuenta, así que se recorta lo que sobre.
        this.endpoint = normalizeEndpoint(endpoint);
        this.bucket = bucket == null ? "" : bucket.trim();
        this.accessKeyId = accessKeyId == null ? "" : accessKeyId.trim();
        this.secretAccessKey = secretAccessKey == null ? "" : secretAccessKey.trim();
    }

    private static String normalizeEndpoint(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        int schemeEnd = value.indexOf("://");
        int firstSlash = value.indexOf('/', schemeEnd < 0 ? 0 : schemeEnd + 3);
        return firstSlash < 0 ? value : value.substring(0, firstSlash);
    }

    @Override
    public boolean isEnabled() {
        return !endpoint.isBlank() && !bucket.isBlank()
                && !accessKeyId.isBlank() && !secretAccessKey.isBlank();
    }

    @Override
    public String upload(String key, byte[] content, String contentType) {
        requireEnabled();
        try {
            s3().putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
            return key;
        } catch (RuntimeException e) {
            log.error("No se pudo subir el archivo {} a R2", key, e);
            throw new APIException("No se pudo guardar el archivo. Intente de nuevo.");
        }
    }

    @Override
    public String signedUrl(String key, Duration ttl) {
        if (key == null || key.isBlank() || !isEnabled()) return null;
        try {
            return presigner().presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(ttl)
                            .getObjectRequest(GetObjectRequest.builder()
                                    .bucket(bucket)
                                    .key(key)
                                    .build())
                            .build())
                    .url()
                    .toString();
        } catch (RuntimeException e) {
            // Que no se pueda firmar no debe tumbar la pantalla completa: se
            // devuelve null y el reporte sale sin imagen.
            log.warn("No se pudo firmar la URL de {}", key, e);
            return null;
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank() || !isEnabled()) return;
        try {
            s3().deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            // Si el borrado falla, el registro ya quedó sin la llave: se queda un
            // objeto huérfano en el bucket, pero no vale la pena romper la operación.
            log.warn("No se pudo borrar {} de R2", key, e);
        }
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new APIException("El almacenamiento de archivos no está configurado. "
                    + "Defina las credenciales del bucket R2 (app.r2.*).");
        }
    }

    private S3Client s3() {
        S3Client local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = S3Client.builder()
                            .endpointOverride(URI.create(endpoint))
                            .region(Region.of("auto"))
                            .credentialsProvider(credentials())
                            .httpClient(UrlConnectionHttpClient.create())
                            // Desde la 2.30 el SDK manda un checksum CRC32 en cada
                            // PUT; R2 no siempre lo acepta y responde 400. Solo se
                            // envía cuando la operación lo exige de verdad.
                            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                            .serviceConfiguration(S3Configuration.builder()
                                    .pathStyleAccessEnabled(true)
                                    .chunkedEncodingEnabled(false)
                                    .build())
                            .build();
                    client = local;
                }
            }
        }
        return local;
    }

    private S3Presigner presigner() {
        S3Presigner local = presigner;
        if (local == null) {
            synchronized (this) {
                local = presigner;
                if (local == null) {
                    local = S3Presigner.builder()
                            .endpointOverride(URI.create(endpoint))
                            .region(Region.of("auto"))
                            .credentialsProvider(credentials())
                            .serviceConfiguration(S3Configuration.builder()
                                    .pathStyleAccessEnabled(true)
                                    .chunkedEncodingEnabled(false)
                                    .build())
                            .build();
                    presigner = local;
                }
            }
        }
        return local;
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
    }

    @PreDestroy
    void close() {
        if (client != null) client.close();
        if (presigner != null) presigner.close();
    }
}
