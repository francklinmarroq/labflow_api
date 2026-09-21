package marroquinsoftware.labflowapi.repositories;

import marroquinsoftware.labflowapi.model.ReferringPhysician;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReferringPhysicianRepository extends JpaRepository<ReferringPhysician, Long> {

    // El laboratorio (tenant) lo filtra Hibernate por @TenantId, así que estas
    // consultas ya solo ven los médicos del laboratorio de la sesión.
    List<ReferringPhysician> findAllByOrderByNameAsc();

    Optional<ReferringPhysician> findByNormalizedName(String normalizedName);

    /**
     * Cuántas órdenes tiene cada médico, para enseñar en el catálogo qué tan usado
     * está antes de corregirle el nombre o borrarlo. Solo devuelve los que tienen al
     * menos una orden; los demás cuentan cero.
     *
     * <p>Va en JPQL y no en SQL nativo a propósito: recorriendo las entidades,
     * Hibernate vacía los cambios pendientes antes de consultar (con SQL nativo no
     * sabe qué tablas toca la consulta y puede leer sin las inserciones recién
     * hechas). Además el laboratorio lo filtra solo, por el {@code @TenantId} de
     * LabOrder.
     */
    @Query("select p.id, count(o.id) from LabOrder o join o.referringPhysician p group by p.id")
    List<Object[]> countUsageByPhysician();

    /**
     * Quita el médico de todas las órdenes que lo tenían. Hace falta antes de
     * borrarlo: la orden apunta al médico con una llave foránea, así que el DELETE
     * fallaría mientras alguna la tenga puesta. Las órdenes quedan intactas, solo
     * dejan de indicar médico solicitante. El id ya viene validado contra el tenant
     * por el servicio.
     *
     * <p>A diferencia del equivalente de las etiquetas, esta va en JPQL y no en SQL
     * nativo: acá la dueña de la relación es una entidad (LabOrder), así que no hace
     * falta saber el nombre de la columna y la consulta se queda dentro del filtro
     * de tenant. Lleva flush y clear por lo mismo que aquella: sin el flush se
     * podría perder un enganche recién hecho, y sin el clear las órdenes que ya
     * estén cargadas en la sesión seguirían mostrando un médico que en la base ya no
     * tienen.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LabOrder o set o.referringPhysician = null where o.referringPhysician.id = :physicianId")
    void detachFromAllOrders(@Param("physicianId") Long physicianId);
}
