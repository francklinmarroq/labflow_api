package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.BillingClient;
import marroquinsoftware.labflowapi.payload.BillingClientDTO;
import marroquinsoftware.labflowapi.payload.BillingClientResponse;
import marroquinsoftware.labflowapi.repositories.BillingClientRepository;
import marroquinsoftware.labflowapi.repositories.InvoiceRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BillingClientServiceImp implements BillingClientService {

    @Autowired
    private BillingClientRepository billingClientRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public BillingClientResponse getAllBillingClients(Integer pageNumber, Integer pageSize,
                                                      String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<BillingClient> page = billingClientRepository.findAll(pageable);
        List<BillingClientDTO> dtos = page.getContent().stream()
                .map(c -> modelMapper.map(c, BillingClientDTO.class))
                .toList();
        BillingClientResponse response = new BillingClientResponse();
        response.setContent(dtos);
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    @Override
    public BillingClientDTO getBillingClient(Long id) {
        return modelMapper.map(findOrFail(id), BillingClientDTO.class);
    }

    @Override
    public BillingClientDTO createBillingClient(BillingClientDTO dto) {
        String rtn = normalize(dto.getRtn());
        requireRtnIsFree(rtn, null);
        BillingClient client = new BillingClient();
        apply(client, dto, rtn);
        return modelMapper.map(billingClientRepository.save(client), BillingClientDTO.class);
    }

    @Override
    public BillingClientDTO updateBillingClient(BillingClientDTO dto, Long id) {
        BillingClient client = findOrFail(id);
        String rtn = normalize(dto.getRtn());
        requireRtnIsFree(rtn, id);
        // Editar no reescribe las facturas ya emitidas: llevan congelados el
        // nombre y el RTN con los que salieron.
        apply(client, dto, rtn);
        return modelMapper.map(billingClientRepository.save(client), BillingClientDTO.class);
    }

    @Override
    public BillingClientDTO deleteBillingClient(Long id) {
        BillingClient client = findOrFail(id);
        // Las anuladas también bloquean: su identidad sigue impresa en un
        // documento fiscal que quedaría apuntando a la nada.
        if (invoiceRepository.existsByBillingClientId(id)) {
            throw new APIException("No se puede eliminar el cliente «" + client.getName()
                    + "»: tiene facturas emitidas a su nombre.");
        }
        billingClientRepository.delete(client);
        return modelMapper.map(client, BillingClientDTO.class);
    }

    private BillingClient findOrFail(Long id) {
        return billingClientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BillingClient", "billingClientId", id));
    }

    /**
     * El RTN es único por laboratorio. Se comprueba acá y no se deja reventar la
     * restricción única: el 409 crudo de la base no dice a quién pertenece ya, y
     * sin ese nombre quien atiende no sabe a qué ficha ir.
     *
     * @param selfId id del cliente que se está editando, para no chocar consigo mismo
     */
    private void requireRtnIsFree(String rtn, Long selfId) {
        if (rtn == null || !billingClientRepository.existsByRtn(rtn)) return;
        // Solo cuando de verdad está ocupado se trae la ficha, que es lo único
        // que hace falta para nombrarla en el mensaje.
        BillingClient existing = billingClientRepository.findByRtn(rtn);
        if (existing != null && !existing.getId().equals(selfId)) {
            throw new APIException("El RTN " + rtn + " ya pertenece al cliente «"
                    + existing.getName() + "».");
        }
    }

    private void apply(BillingClient client, BillingClientDTO dto, String rtn) {
        client.setName(normalize(dto.getName()));
        client.setRtn(rtn);
        client.setPhone(normalize(dto.getPhone()));
        client.setEmail(normalize(dto.getEmail()));
        client.setAddress(normalize(dto.getAddress()));
    }

    /** Recorta espacios; los campos opcionales en blanco quedan nulos. */
    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
