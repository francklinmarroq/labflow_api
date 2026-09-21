package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.model.ReferringPhysician;
import marroquinsoftware.labflowapi.payload.ReferringPhysicianDTO;
import marroquinsoftware.labflowapi.payload.ReferringPhysicianResponse;

public interface ReferringPhysicianService {

    /** Médicos del laboratorio ordenados por nombre, con el conteo de órdenes que los tienen. */
    ReferringPhysicianResponse getAllPhysicians();

    ReferringPhysicianDTO createPhysician(ReferringPhysicianDTO dto);

    /** Corrige el nombre. Corregirlo afecta a todas las órdenes que ya lo tienen. */
    ReferringPhysicianDTO updatePhysician(ReferringPhysicianDTO dto, Long id);

    /** Borra el médico y lo quita de las órdenes que lo tenían; no toca las órdenes. */
    ReferringPhysicianDTO deletePhysician(Long id);

    /**
     * Traduce el nombre escrito en una orden a un médico del catálogo, creándolo si
     * aún no existe en el laboratorio. Es el "se guarda la primera vez que se use":
     * quien levanta la orden escribe "Dra. Ana Fúnez" y el médico queda disponible
     * para las siguientes órdenes sin haber pasado por el catálogo.
     *
     * <p>Devuelve {@code null} si el nombre viene vacío o en blanco — la orden
     * simplemente no indica médico solicitante — y en ese caso no crea nada.
     */
    ReferringPhysician resolveOrCreate(String name);
}
