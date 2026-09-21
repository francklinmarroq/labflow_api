package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.TestMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TestMethodRepository extends JpaRepository<TestMethod, Long> {

    // El laboratorio (tenant) lo filtra Hibernate por @TenantId, así que estas
    // consultas ya solo ven los métodos del laboratorio de la sesión; por eso
    // ninguna firma lleva laboratoryId.

    /**
     * El método de ESE perfil que se escribe así. La unicidad del nombre es por
     * perfil: el mismo nombre bajo otro perfil es otro método y no lo devuelve.
     */
    Optional<TestMethod> findByTestConfig_IdAndNormalizedName(Long testConfigId, String normalizedName);

    /** Los métodos del perfil, en el orden en que se ofrecen al elegir. */
    List<TestMethod> findByTestConfig_IdOrderByNameAsc(Long testConfigId);

    /**
     * Cuántos exámenes de órdenes indican este método. Es lo que decide si se puede
     * quitar del perfil: el método está impreso en reportes ya entregados, así que
     * uno en uso no se borra — se renombra o se deja de marcar como predeterminado.
     * El conteo entra en el mensaje del rechazo para que se sepa de antemano el
     * tamaño del trabajo de liberarlo.
     *
     * <p>Va en JPQL y no en SQL nativo a propósito: recorriendo las entidades,
     * Hibernate vacía los cambios pendientes antes de consultar (con SQL nativo no
     * sabe qué tablas toca la consulta y podría contar sin los enganches recién
     * hechos). Además el laboratorio lo filtra solo, por el {@code @TenantId} de
     * LabTest.
     */
    @Query("select count(t.id) from LabTest t where t.method.id = :methodId")
    long countLabTestsUsing(@Param("methodId") Long methodId);
}
