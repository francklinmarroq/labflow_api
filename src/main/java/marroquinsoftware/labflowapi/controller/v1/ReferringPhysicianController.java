package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.payload.ReferringPhysicianDTO;
import marroquinsoftware.labflowapi.payload.ReferringPhysicianResponse;
import marroquinsoftware.labflowapi.service.ReferringPhysicianService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Médicos que refieren trabajo al laboratorio. No hay endpoint de alta
 * obligatorio: el médico nace solo la primera vez que se escribe su nombre en una
 * orden. Estos endpoints son para consultarlos (el autocompletado al levantar la
 * orden y el filtro del listado) y para mantenerlos después: corregirles el nombre
 * o borrarlos.
 */
@RestController
@RequestMapping("/api/v1/referring-physicians")
public class ReferringPhysicianController {

    @Autowired
    private ReferringPhysicianService referringPhysicianService;

    // Lectura: la necesitan el autocompletado de la orden y el filtro del listado,
    // además de la pantalla del catálogo. Por eso es tan amplia como la de las
    // etiquetas: quien levanta una orden tiene ORDERS_CREATE, no CATALOG_VIEW.
    @GetMapping
    @PreAuthorize("hasAnyAuthority('CATALOG_VIEW','ORDERS_VIEW','ORDERS_CREATE','INVOICES_VIEW','INVOICES_CREATE','REPORTS_VIEW')")
    public ResponseEntity<ReferringPhysicianResponse> getAllPhysicians() {
        return new ResponseEntity<>(referringPhysicianService.getAllPhysicians(), HttpStatus.OK);
    }

    // Alta explícita desde el catálogo (para dejar preparada la lista del
    // laboratorio). El alta implícita al escribir el médico en una orden va con
    // ORDERS_CREATE y no pasa por acá.
    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_CREATE')")
    public ResponseEntity<ReferringPhysicianDTO> createPhysician(@Valid @RequestBody ReferringPhysicianDTO dto) {
        return new ResponseEntity<>(referringPhysicianService.createPhysician(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{physicianId}")
    @PreAuthorize("hasAuthority('CATALOG_EDIT')")
    public ResponseEntity<ReferringPhysicianDTO> updatePhysician(@Valid @RequestBody ReferringPhysicianDTO dto,
                                                                 @PathVariable Long physicianId) {
        return new ResponseEntity<>(referringPhysicianService.updatePhysician(dto, physicianId), HttpStatus.OK);
    }

    @DeleteMapping("/{physicianId}")
    @PreAuthorize("hasAuthority('CATALOG_DELETE')")
    public ResponseEntity<ReferringPhysicianDTO> deletePhysician(@PathVariable Long physicianId) {
        return new ResponseEntity<>(referringPhysicianService.deletePhysician(physicianId), HttpStatus.OK);
    }
}
