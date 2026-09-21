package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.TestConfig;
import marroquinsoftware.labflowapi.model.TestMethod;
import marroquinsoftware.labflowapi.payload.TestMethodDTO;
import marroquinsoftware.labflowapi.repositories.TestMethodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class TestMethodServiceImp implements TestMethodService {

    private static final int MAX_NAME_LENGTH = 255;

    @Autowired
    private TestMethodRepository testMethodRepository;

    @Override
    @Transactional
    public TestMethod resolveOrCreate(TestConfig testConfig, String name) {
        if (name == null || name.isBlank()) {
            // Sin nombre el examen simplemente no indica método; no se crea nada y
            // el perfil no se entera (tampoco su predeterminado).
            return null;
        }
        String cleaned = cleanName(name);
        String normalized = normalize(cleaned);
        Optional<TestMethod> existing =
                testMethodRepository.findByTestConfig_IdAndNormalizedName(testConfig.getId(), normalized);
        if (existing.isPresent()) {
            return existing.get();
        }
        // Primera vez que se escribe este método en este perfil: se da de alta solo,
        // sin pasar por el editor del examen. Ese es el punto de la feature.
        TestMethod method = new TestMethod();
        // El laboratorio (tenant) lo asigna Hibernate al persistir por @TenantId.
        method.setTestConfig(testConfig);
        method.setName(cleaned);
        method.setNormalizedName(normalized);
        TestMethod saved = testMethodRepository.save(method);
        // Se agrega también a la colección del perfil: si ya estaba cargada en la
        // sesión, quien la lea después (markAsDefault, el DTO del perfil) tiene que
        // ver el método recién creado y no una lista de antes.
        testConfig.getMethods().add(saved);
        return saved;
    }

    @Override
    @Transactional
    public void markAsDefault(TestMethod method) {
        // Se recorre la colección del perfil (son unos pocos métodos) en vez de un
        // update masivo: así el "a lo sumo uno" queda escrito en un solo lugar y las
        // entidades ya cargadas en la sesión quedan diciendo lo mismo que la base.
        for (TestMethod other : method.getTestConfig().getMethods()) {
            if (other.isDefaultMethod() && !other.getId().equals(method.getId())) {
                other.setDefaultMethod(false);
            }
        }
        method.setDefaultMethod(true);
    }

    @Override
    @Transactional
    public void reconcile(TestConfig testConfig, List<TestMethodDTO> incoming) {
        List<TestMethodDTO> rows = incoming != null ? incoming : List.of();
        List<TestMethod> current = new ArrayList<>(testConfig.getMethods());

        Map<Long, TestMethod> byId = new HashMap<>();
        Map<String, TestMethod> byNormalized = new HashMap<>();
        for (TestMethod method : current) {
            byId.put(method.getId(), method);
            byNormalized.putIfAbsent(method.getNormalizedName(), method);
        }

        // Lo que el perfil debe tener al terminar, indexado por la llave de
        // comparación: un método por llave, sea uno existente o uno nuevo.
        Map<String, Target> targets = new LinkedHashMap<>();
        Set<Long> claimed = new HashSet<>();
        boolean defaultTaken = false;

        for (TestMethodDTO row : rows) {
            if (row == null || row.getName() == null || row.getName().isBlank()) {
                // Un nombre en blanco no llega a ser método; no es un error.
                continue;
            }
            String name = cleanName(row.getName());
            String normalized = normalize(name);
            boolean wantsDefault = Boolean.TRUE.equals(row.getIsDefault());

            TestMethod existing = row.getId() != null ? byId.get(row.getId()) : null;
            if (existing == null) {
                // Sin id (o con uno que este perfil no conoce): si el perfil ya tiene
                // un método que se escribe igual, es ESE, salvo que otra fila ya se lo
                // haya llevado — en cuyo caso este es uno nuevo con ese nombre.
                TestMethod sameName = byNormalized.get(normalized);
                if (sameName != null && !claimed.contains(sameName.getId())) {
                    existing = sameName;
                }
            }

            Target already = targets.get(normalized);
            if (already != null) {
                // El mismo método escrito dos veces en un mismo guardado. Si las dos
                // filas son métodos DISTINTOS del perfil, renombrar una sobre la otra
                // fusionaría dos técnicas en los reportes que las nombran: se rechaza.
                if (existing != null && already.existing != null
                        && !existing.getId().equals(already.existing.getId())) {
                    throw new APIException("El perfil ya tiene el método '" + already.name
                            + "'. Dos métodos del mismo perfil no pueden llamarse igual.");
                }
                // Si no, es el mismo método nombrado otra vez: queda uno solo.
                if (wantsDefault && !defaultTaken) {
                    already.isDefault = true;
                    defaultTaken = true;
                }
                continue;
            }

            if (existing != null) {
                claimed.add(existing.getId());
            }
            boolean isDefault = wantsDefault && !defaultTaken;
            defaultTaken |= isDefault;
            targets.put(normalized, new Target(existing, name, normalized, isDefault));
        }

        // Lo que ya no viene se quita, salvo que alguna orden lo indique: eso se
        // rechaza y con ello se revierte el guardado entero del examen.
        for (TestMethod method : current) {
            if (claimed.contains(method.getId())) {
                continue;
            }
            long inUse = testMethodRepository.countLabTestsUsing(method.getId());
            if (inUse > 0) {
                throw new APIException("No se puede quitar el método '" + method.getName() + "': "
                        + inUse + (inUse == 1 ? " examen de una orden lo indica" : " exámenes de órdenes lo indican")
                        + ". Está impreso en los reportes ya entregados. Cámbieles el método a esos exámenes "
                        + "y vuelva a intentarlo, o renómbrelo en vez de quitarlo.");
            }
            // orphanRemoval borra la fila al quitarla de la colección del perfil. Si
            // era el predeterminado, el perfil queda SIN predeterminado: no se asciende
            // a ningún otro.
            testConfig.getMethods().remove(method);
        }

        for (Target target : targets.values()) {
            TestMethod method = target.existing;
            if (method == null) {
                method = new TestMethod();
                method.setTestConfig(testConfig);
                testConfig.getMethods().add(method);
            }
            // Renombrar corrige cómo se escribe la técnica en TODAS las órdenes que ya
            // la indicaban, reportes incluidos: el examen apunta al método, no a una
            // copia del texto.
            method.setName(target.name);
            method.setNormalizedName(target.normalized);
            method.setDefaultMethod(target.isDefault);
        }
    }

    @Override
    public List<TestMethodDTO> toDTOs(TestConfig testConfig) {
        // La colección ya viene ordenada por nombre (@OrderBy), que es el orden en
        // que se ofrecen al elegir.
        return testConfig.getMethods().stream()
                .map(method -> new TestMethodDTO(method.getId(), method.getName(), method.isDefaultMethod()))
                .toList();
    }

    // --- Helpers ---

    /** Cómo queda un método del perfil después del guardado. */
    private static final class Target {
        private final TestMethod existing;
        private final String name;
        private final String normalized;
        private boolean isDefault;

        private Target(TestMethod existing, String name, String normalized, boolean isDefault) {
            this.existing = existing;
            this.name = name;
            this.normalized = normalized;
            this.isDefault = isDefault;
        }
    }

    /** Recorta, colapsa espacios repetidos y corta al largo de la columna. */
    private String cleanName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new APIException("Escriba el nombre del método.");
        }
        String name = raw.trim().replaceAll("\\s+", " ");
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    /**
     * Llave de comparación del método: sin tildes, sin espacios de más y en
     * minúsculas, para que "Quimioluminiscencia", "quimioluminiscencia" y
     * "Quimioluminiscencia " sean el mismo y el perfil no se llene de variantes de
     * la misma técnica. Se descompone en NFD y se quitan los diacríticos, así
     * "Aglutinación" y "Aglutinacion" también coinciden. Es la MISMA regla de
     * {@code OrderTagServiceImp} y {@code ReferringPhysicianServiceImp}.
     */
    private String normalize(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase();
    }
}
