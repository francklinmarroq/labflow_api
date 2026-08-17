package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.payload.OrderTagDTO;
import marroquinsoftware.labflowapi.payload.OrderTagResponse;
import marroquinsoftware.labflowapi.service.OrderTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Etiquetas con las que el laboratorio clasifica sus órdenes (convenios como
 * "IHSS", campañas, empresas). No hay endpoint de alta obligatorio: la etiqueta
 * nace sola la primera vez que se escribe en una orden. Estos endpoints son para
 * consultarlas (el autocompletado al levantar la orden y los filtros de los
 * listados) y para mantenerlas después: renombrarlas, darles color o borrarlas.
 */
@RestController
@RequestMapping("/api/v1/order-tags")
public class OrderTagController {

    @Autowired
    private OrderTagService orderTagService;

    // Lectura: la necesitan el autocompletado de la orden y los filtros de los
    // listados de órdenes y facturas, además de la pantalla del catálogo.
    @GetMapping
    @PreAuthorize("hasAnyAuthority('CATALOG_VIEW','ORDERS_VIEW','ORDERS_CREATE','INVOICES_VIEW','INVOICES_CREATE','REPORTS_VIEW')")
    public ResponseEntity<OrderTagResponse> getAllTags() {
        return new ResponseEntity<>(orderTagService.getAllTags(), HttpStatus.OK);
    }

    // Alta explícita desde el catálogo (para dejar preparadas las etiquetas del
    // laboratorio). El alta implícita al etiquetar una orden va con ORDERS_CREATE
    // y no pasa por acá.
    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_CREATE')")
    public ResponseEntity<OrderTagDTO> createTag(@Valid @RequestBody OrderTagDTO dto) {
        return new ResponseEntity<>(orderTagService.createTag(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{tagId}")
    @PreAuthorize("hasAuthority('CATALOG_EDIT')")
    public ResponseEntity<OrderTagDTO> updateTag(@Valid @RequestBody OrderTagDTO dto, @PathVariable Long tagId) {
        return new ResponseEntity<>(orderTagService.updateTag(dto, tagId), HttpStatus.OK);
    }

    @DeleteMapping("/{tagId}")
    @PreAuthorize("hasAuthority('CATALOG_DELETE')")
    public ResponseEntity<OrderTagDTO> deleteTag(@PathVariable Long tagId) {
        return new ResponseEntity<>(orderTagService.deleteTag(tagId), HttpStatus.OK);
    }
}
