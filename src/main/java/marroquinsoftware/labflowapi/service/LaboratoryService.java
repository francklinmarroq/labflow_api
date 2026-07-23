package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.LaboratoryDTO;
import org.springframework.web.multipart.MultipartFile;

public interface LaboratoryService {
    LaboratoryDTO getLaboratory();
    LaboratoryDTO createLaboratory(LaboratoryDTO dto);
    LaboratoryDTO updateLaboratory(LaboratoryDTO dto, Long id);

    /** Sube (o reemplaza) el logo del laboratorio de la sesión. */
    LaboratoryDTO uploadLogo(MultipartFile file);

    /** Quita el logo; los reportes vuelven a salir sin imagen. */
    LaboratoryDTO deleteLogo();

    /** Sube (o reemplaza) el sello del regente del laboratorio de la sesión. */
    LaboratoryDTO uploadStamp(MultipartFile file);

    /** Quita el sello; el reporte vuelve a salir solo con la línea de firma. */
    LaboratoryDTO deleteStamp();
}
