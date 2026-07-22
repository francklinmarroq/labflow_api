package marroquinsoftware.labflowapi.controller.v1;

import marroquinsoftware.labflowapi.payload.ReferenceRangeDTO;
import marroquinsoftware.labflowapi.service.ReferenceRangeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lectura por lote de rangos de referencia, fuera del árbol
 * /parameters/{parameterId}/reference-ranges.
 *
 * Existe por rendimiento: abrir una orden necesitaba los rangos de todos sus
 * parámetros y los pedía de a uno, así que un hemograma se iba en veintipico de
 * llamadas HTTP. Contra la API en Cloudflare Containers cada llamada cuesta
 * ~0.7 s de piso, así que el ahorro es de segundos, no de milisegundos.
 */
@RestController
@RequestMapping("/api/v1/reference-ranges")
public class ReferenceRangeBatchController {

    @Autowired
    private ReferenceRangeService referenceRangeService;

    /**
     * Devuelve la lista plana de rangos de los parámetros pedidos. Cada DTO trae
     * su parameterId, así que el cliente agrupa sin necesidad de un mapa anidado.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('CATALOG_VIEW','ORDERS_VIEW','ORDERS_CREATE','ORDERS_ENTER_RESULTS','ORDERS_PRINT')")
    public ResponseEntity<List<ReferenceRangeDTO>> getRangesByParameterIds(
            @RequestParam(required = false) List<Long> parameterIds) {
        return new ResponseEntity<>(
                referenceRangeService.getRangesByParameterIds(parameterIds),
                HttpStatus.OK);
    }
}
