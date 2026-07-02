package marroquinsoftware.labflowapi.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Conecta el {@link TenantContext} con la multi-tenancy por discriminador de
 * Hibernate (entidades anotadas con {@code @TenantId}).
 *
 * <p>Hibernate llama a {@link #resolveCurrentTenantIdentifier()} en cada sesión
 * para: (1) filtrar automáticamente los SELECT/UPDATE/DELETE por laboratorio y
 * (2) setear el laboratorio al insertar. Así el aislamiento no depende de que
 * cada consulta recuerde filtrar.
 *
 * <p>Spring Boot 4 no auto-detecta este bean, por eso implementamos también
 * {@link HibernatePropertiesCustomizer} para registrarlo en la EMF.
 */
@Component
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    /**
     * Valor usado cuando no hay tenant en el hilo (peticiones no autenticadas que
     * igual tocaran una entidad de tenant). No corresponde a ningún laboratorio,
     * de modo que las consultas no devuelven nada en vez de filtrar de otro lab.
     */
    private static final Long NO_TENANT = -1L;

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long laboratoryId = TenantContext.getLaboratoryId();
        return laboratoryId != null ? laboratoryId : NO_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
