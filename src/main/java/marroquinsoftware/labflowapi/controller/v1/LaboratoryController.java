package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.payload.LaboratoryDTO;
import marroquinsoftware.labflowapi.service.LaboratoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/laboratory")
public class LaboratoryController {

    @Autowired
    private LaboratoryService laboratoryService;

    @GetMapping
    public ResponseEntity<LaboratoryDTO> getLaboratory() {
        return new ResponseEntity<>(laboratoryService.getLaboratory(), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<LaboratoryDTO> createLaboratory(@Valid @RequestBody LaboratoryDTO dto) {
        return new ResponseEntity<>(laboratoryService.createLaboratory(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{laboratoryId}")
    public ResponseEntity<LaboratoryDTO> updateLaboratory(@Valid @RequestBody LaboratoryDTO dto, @PathVariable Long laboratoryId) {
        return new ResponseEntity<>(laboratoryService.updateLaboratory(dto, laboratoryId), HttpStatus.OK);
    }
}
