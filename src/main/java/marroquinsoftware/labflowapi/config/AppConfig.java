package marroquinsoftware.labflowapi.config;

import org.modelmapper.ModelMapper;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@ImportRuntimeHints(AppConfig.NativeRuntimeHints.class)
public class AppConfig {
    @Bean
    public ModelMapper modelMapper(){
        return new ModelMapper();
    }

    /**
     * Hints de reflexion para la imagen nativa de GraalVM. Se registran por el canal
     * de Spring AOT (no por archivos sueltos en META-INF) porque el AOT los emite en
     * la configuracion que native-image consume siempre, sin depender de que escanee
     * el classpath a mano.
     */
    static class NativeRuntimeHints implements RuntimeHintsRegistrar {
        private static final Pattern CLASS_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
        private static final String PAYLOAD_CLASSES =
                "classpath*:marroquinsoftware/labflowapi/payload/**/*.class";

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // pgjdbc instancia la SSLSocketFactory por reflexion (Class.forName +
            // getConstructor(Properties.class)). Sin esto el arranque revienta con
            // "DefaultJavaSSLFactory could not be instantiated" al conectar por SSL a
            // PlanetScale (sslmode=verify-full + esta factory, que usa el truststore JVM).
            hints.reflection().registerTypeIfPresent(classLoader,
                    "org.postgresql.ssl.DefaultJavaSSLFactory",
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

            // jjwt carga sus clases de implementacion por nombre (Classes.forName en
            // Jwts.<clinit>). El jar jjwt-impl no trae metadata de reachability, asi que
            // sin registrarlas el primer login/parse de JWT revienta con
            // "UnknownClassException: DefaultJwtBuilder$Supplier". Se registran las 350
            // clases del jar, listadas en el reflect-config generado del propio jar.
            registerJjwtImplClasses(hints, classLoader);

            // DTOs de request/response para binding de Jackson. Los que se devuelven via
            // ResponseEntity<?> (comodin) no los detecta el AOT —el ? borra el tipo—, asi
            // que sin registrarlos Jackson no encuentra sus getters y serializa "{}" (p.ej.
            // el login respondia 200 con body vacio y sin token). Se registra el paquete
            // payload completo para cubrir todos los endpoints, no solo el login.
            registerPayloadBinding(hints, classLoader);
        }

        private void registerJjwtImplClasses(RuntimeHints hints, ClassLoader classLoader) {
            ClassPathResource resource = new ClassPathResource(
                    "META-INF/native-image/io.jsonwebtoken/jjwt-impl/reflect-config.json");
            if (!resource.exists()) {
                return;
            }
            try (InputStream in = resource.getInputStream()) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Matcher matcher = CLASS_NAME.matcher(json);
                while (matcher.find()) {
                    hints.reflection().registerTypeIfPresent(classLoader, matcher.group(1),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo leer el reflect-config de jjwt-impl", e);
            }
        }

        private void registerPayloadBinding(RuntimeHints hints, ClassLoader classLoader) {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
            MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);
            BindingReflectionHintsRegistrar binding = new BindingReflectionHintsRegistrar();
            try {
                for (Resource resource : resolver.getResources(PAYLOAD_CLASSES)) {
                    String className = readerFactory.getMetadataReader(resource).getClassMetadata().getClassName();
                    Class<?> type = ClassUtils.forName(className, classLoader);
                    binding.registerReflectionHints(hints.reflection(), type);
                }
            } catch (IOException | ClassNotFoundException e) {
                throw new IllegalStateException("No se pudieron registrar los DTOs de payload para nativo", e);
            }
        }
    }
}
