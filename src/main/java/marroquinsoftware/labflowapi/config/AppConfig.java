package marroquinsoftware.labflowapi.config;

import org.modelmapper.ModelMapper;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(AppConfig.NativeRuntimeHints.class)
public class AppConfig {
    @Bean
    public ModelMapper modelMapper(){
        return new ModelMapper();
    }

    // En la imagen nativa de GraalVM, pgjdbc instancia la SSLSocketFactory por
    // reflexion (Class.forName + getConstructor(Properties.class)). Como la metadata
    // de reachability de postgresql no cubre esta clase, native-image no la incluye y
    // el arranque revienta con "DefaultJavaSSLFactory could not be instantiated". La
    // conexion a PlanetScale usa sslmode=verify-full + esta factory (toma el truststore
    // de la JVM), asi que hay que declararla como reachable para reflexion.
    static class NativeRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerTypeIfPresent(classLoader,
                    "org.postgresql.ssl.DefaultJavaSSLFactory",
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
    }
}
