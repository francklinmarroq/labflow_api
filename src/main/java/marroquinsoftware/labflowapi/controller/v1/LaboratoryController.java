package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.payload.LaboratoryDTO;
import marroquinsoftware.labflowapi.service.LaboratoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/laboratory")
public class LaboratoryController {

    @Autowired
    private LaboratoryService laboratoryService;

    // La impresión de órdenes y cotizaciones también lee los datos del
    // laboratorio (membrete).
    @GetMapping
    @PreAuthorize("hasAnyAuthority('LAB_SETTINGS_VIEW','LAB_SETTINGS_EDIT','ORDERS_PRINT','QUOTES_VIEW','QUOTES_CREATE')")
    public ResponseEntity<LaboratoryDTO> getLaboratory() {
        return new ResponseEntity<>(laboratoryService.getLaboratory(), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LAB_SETTINGS_EDIT')")
    public ResponseEntity<LaboratoryDTO> createLaboratory(@Valid @RequestBody LaboratoryDTO dto) {
        return new ResponseEntity<>(laboratoryService.createLaboratory(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{laboratoryId}")
    @PreAuthorize("hasAuthority('LAB_SETTINGS_EDIT')")
    public ResponseEntity<LaboratoryDTO> updateLaboratory(@Valid @RequestBody LaboratoryDTO dto, @PathVariable Long laboratoryId) {
        return new ResponseEntity<>(laboratoryService.updateLaboratory(dto, laboratoryId), HttpStatus.OK);
    }

    // El archivo sube por acá y no directo al bucket: así el bucket queda privado
    // sin CORS ni llaves en el navegador. La respuesta ya trae la URL firmada nueva.
    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('LAB_SETTINGS_EDIT')")
    public ResponseEntity<LaboratoryDTO> uploadLogo(@RequestParam("file") MultipartFile file) {
        return new ResponseEntity<>(laboratoryService.uploadLogo(file), HttpStatus.OK);
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasAuthority('LAB_SETTINGS_EDIT')")
    public ResponseEntity<LaboratoryDTO> deleteLogo() {
        return new ResponseEntity<>(laboratoryService.deleteLogo(), HttpStatus.OK);
    }
}
