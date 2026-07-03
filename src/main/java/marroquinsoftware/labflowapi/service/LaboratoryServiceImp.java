package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.payload.LaboratoryDTO;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LaboratoryServiceImp implements LaboratoryService {

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public LaboratoryDTO getLaboratory() {
        Laboratory laboratory = laboratoryRepository.findById(requireTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", "id", requireTenant()));
        return modelMapper.map(laboratory, LaboratoryDTO.class);
    }

    @Override
    public LaboratoryDTO createLaboratory(LaboratoryDTO dto) {
        // El laboratorio se crea al registrar la cuenta (RegistrationService). No se
        // permite crear otro desde aquí para no dejar laboratorios sin dueño.
        throw new APIException("El laboratorio se crea al registrar la cuenta. Use PUT para actualizarlo.");
    }

    @Override
    public LaboratoryDTO updateLaboratory(LaboratoryDTO dto, Long id) {
        Long tenant = requireTenant();
        if (!tenant.equals(id)) {
            throw new APIException("No puede modificar un laboratorio distinto al suyo.");
        }
        Laboratory laboratory = laboratoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", "id", id));
        // El owner no viene en el DTO, así que modelMapper no lo toca y se conserva.
        modelMapper.map(dto, laboratory);
        laboratory.setId(id);
        return modelMapper.map(laboratoryRepository.save(laboratory), LaboratoryDTO.class);
    }

    private Long requireTenant() {
        Long laboratoryId = TenantContext.getLaboratoryId();
        if (laboratoryId == null) {
            throw new APIException("No hay un laboratorio asociado a la sesión actual");
        }
        return laboratoryId;
    }
}
