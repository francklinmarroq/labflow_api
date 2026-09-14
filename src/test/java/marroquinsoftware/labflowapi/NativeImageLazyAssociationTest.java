package marroquinsoftware.labflowapi;

import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ninguna asociación a-uno del modelo puede ser perezosa.
 *
 * <p>La app se despliega como imagen nativa de GraalVM. El proxy con el que
 * Hibernate representa un {@code @ManyToOne(fetch = LAZY)} sin cargar tiene que
 * existir desde el build; en caliente no se puede fabricar. Una asociación
 * perezosa por lo tanto funciona en toda la suite —que corre en H2 sobre la JVM,
 * donde el proxy se genera sin problema— y revienta en producción con un 500 en
 * CADA lectura de una fila donde ese FK no sea nulo.
 *
 * <p>Ya pasó: {@code Invoice.billingClient} nació LAZY para ahorrarse un LEFT
 * JOIN por listado y tumbó la facturación de develop apenas se emitió la primera
 * factura a nombre de una empresa. Listado, detalle y vista previa devolvían 500;
 * las facturas a paciente seguían bien, porque un FK nulo no crea proxy. Los 116
 * tests estaban en verde.
 *
 * <p>Esta prueba es la red que faltaba. No prueba el comportamiento —eso solo lo
 * prueba la imagen nativa— sino la condición que lo rompe. Si de verdad hace falta
 * una asociación perezosa, hay que registrar su proxy en
 * {@code AppConfig.NativeRuntimeHints}, verificarlo en la imagen nativa (no acá) y
 * recién entonces agregar el campo a {@link #REGISTERED_LAZY_PROXIES}.
 */
class NativeImageLazyAssociationTest {

    /** Campos a-uno perezosos cuyo proxy SÍ está registrado para la imagen nativa. */
    private static final Set<String> REGISTERED_LAZY_PROXIES = Set.of();

    private static final String MODEL_CLASSES =
            "classpath*:marroquinsoftware/labflowapi/model/**/*.class";

    @Test
    void noToOneAssociationIsLazy() throws Exception {
        List<String> offenders = new ArrayList<>();
        var resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);

        for (Resource resource : resolver.getResources(MODEL_CLASSES)) {
            String className = readerFactory.getMetadataReader(resource).getClassMetadata().getClassName();
            Class<?> type = ClassUtils.forName(className, getClass().getClassLoader());
            for (Field field : type.getDeclaredFields()) {
                ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
                OneToOne oneToOne = field.getAnnotation(OneToOne.class);
                FetchType fetch = manyToOne != null ? manyToOne.fetch()
                        : oneToOne != null ? oneToOne.fetch() : null;
                if (fetch != FetchType.LAZY) continue;
                String name = type.getSimpleName() + "." + field.getName();
                if (!REGISTERED_LAZY_PROXIES.contains(name)) {
                    offenders.add(name);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "Asociación a-uno perezosa sin proxy registrado para la imagen nativa: " + offenders
                        + ". En la imagen nativa toda lectura de una fila con ese FK no nulo responde 500, "
                        + "y esta suite (H2 sobre la JVM) no lo detecta. Quitale el fetch = LAZY, o registrá "
                        + "su proxy en AppConfig.NativeRuntimeHints, probalo en la imagen nativa y agregalo "
                        + "a REGISTERED_LAZY_PROXIES.");
    }
}
