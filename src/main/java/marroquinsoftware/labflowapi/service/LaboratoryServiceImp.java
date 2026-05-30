package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Laboratory;
import marroquinsoftware.labflowapi.payload.LaboratoryDTO;
import marroquinsoftware.labflowapi.repositories.LaboratoryRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LaboratoryServiceImp implements LaboratoryService {

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public LaboratoryDTO getLaboratory() {
        List<Laboratory> labs = laboratoryRepository.findAll();
        if (labs.isEmpty()) {
            throw new ResourceNotFoundException("Laboratory", "id", 0L);
        }
        return modelMapper.map(labs.get(0), LaboratoryDTO.class);
    }

    @Override
    public LaboratoryDTO createLaboratory(LaboratoryDTO dto) {
        if (!laboratoryRepository.findAll().isEmpty()) {
            throw new APIException("Laboratory configuration already exists. Use PUT to update it.");
        }
        Laboratory saved = laboratoryRepository.save(modelMapper.map(dto, Laboratory.class));
        return modelMapper.map(saved, LaboratoryDTO.class);
    }

    @Override
    public LaboratoryDTO updateLaboratory(LaboratoryDTO dto, Long id) {
        Laboratory laboratory = laboratoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", "id", id));
        modelMapper.map(dto, laboratory);
        laboratory.setId(id);
        return modelMapper.map(laboratoryRepository.save(laboratory), LaboratoryDTO.class);
    }
}
