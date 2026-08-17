package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.OrderTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderTagRepository extends JpaRepository<OrderTag, Long> {

    // El laboratorio (tenant) lo filtra Hibernate por @TenantId, así que estas
    // consultas ya solo ven las etiquetas del laboratorio de la sesión.
    List<OrderTag> findAllByOrderByNameAsc();

    Optional<OrderTag> findByNormalizedName(String normalizedName);

    /**
     * Resuelve de una sola consulta las etiquetas que ya existen entre las que se
     * escribieron en la orden. Evita un SELECT por nombre al guardar (cada request
     * paga el piso de latencia de la API, pero además cada consulta suma dentro de
     * la misma transacción).
     */
    List<OrderTag> findByNormalizedNameIn(Collection<String> normalizedNames);

    /**
     * Cuántas órdenes usan cada etiqueta, para enseñar en el catálogo qué tan usada
     * está antes de renombrarla o borrarla. Solo devuelve las que tienen al menos
     * una orden; las demás cuentan cero.
     *
     * <p>Va en JPQL y no en SQL nativo a propósito: recorriendo las entidades,
     * Hibernate vacía los cambios pendientes antes de consultar (con SQL nativo no
     * sabe qué tablas toca la consulta y puede leer sin las inserciones recién
     * hechas). Además el laboratorio lo filtra solo, por el {@code @TenantId} de
     * LabOrder.
     */
    @Query("select t.id, count(o.id) from LabOrder o join o.tags t group by t.id")
    List<Object[]> countUsageByTag();

    /**
     * Quita la etiqueta de todas las órdenes que la tenían. Hace falta antes de
     * borrarla: la dueña de la relación es {@code LabOrder}, así que Hibernate no
     * limpia solo las filas de la tabla de unión y el DELETE fallaría por llave
     * foránea. El id ya viene validado contra el tenant por el servicio.
     *
     * <p>Es la única de este repositorio en SQL nativo porque la tabla de unión no
     * tiene entidad. Por eso lleva flush y clear: sin el flush se podrían perder
     * enganches recién hechos, y sin el clear las órdenes que ya estén cargadas en
     * la sesión seguirían mostrando una etiqueta que en la base ya no tienen.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from lab_order_tags where tag_id = :tagId", nativeQuery = true)
    void detachFromAllOrders(@Param("tagId") Long tagId);
}
