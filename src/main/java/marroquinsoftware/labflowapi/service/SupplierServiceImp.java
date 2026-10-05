package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Supplier;
import marroquinsoftware.labflowapi.payload.SupplierDTO;
import marroquinsoftware.labflowapi.payload.SupplierResponse;
import marroquinsoftware.labflowapi.repositories.PurchaseSpecifications;
import marroquinsoftware.labflowapi.repositories.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierServiceImp implements SupplierService {

    @Autowired
    private SupplierRepository supplierRepository;

    @Override
    @Transactional(readOnly = true)
    public SupplierResponse getSuppliers(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                         String search, Boolean active) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Page<Supplier> page = supplierRepository.findAll(
                PurchaseSpecifications.suppliers(search, active), PageRequest.of(pageNumber, pageSize, sort));
        SupplierResponse response = new SupplierResponse();
        response.setContent(page.getContent().stream().map(this::toDTO).toList());
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierDTO getSupplier(Long supplierId) {
        return toDTO(findSupplier(supplierId));
    }

    @Override
    @Transactional
    public SupplierDTO createSupplier(SupplierDTO dto) {
        String rtn = normalize(dto.getRtn());
        requireRtnIsFree(rtn, null);
        Supplier supplier = new Supplier();
        apply(supplier, dto, rtn);
        supplier.setActive(true);
        return toDTO(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierDTO updateSupplier(Long supplierId, SupplierDTO dto) {
        Supplier supplier = findSupplier(supplierId);
        String rtn = normalize(dto.getRtn());
        requireRtnIsFree(rtn, supplierId);
        // Las compras no copian los datos del proveedor: los leen de la ficha, así
        // que corregir un nombre mal escrito lo corrige también en el historial.
        apply(supplier, dto, rtn);
        return toDTO(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierDTO setActive(Long supplierId, boolean active) {
        Supplier supplier = findSupplier(supplierId);
        supplier.setActive(active);
        return toDTO(supplierRepository.save(supplier));
    }

    /**
     * El RTN, si se indica, es único por laboratorio, incluso contra proveedores
     * desactivados: dar de alta otra vez a uno que ya existe partiría su historial
     * en dos fichas. El mensaje nombra a quién pertenece y, si está desactivado,
     * sugiere reactivarlo.
     *
     * @param selfId id del proveedor que se está editando, para no chocar consigo mismo
     */
    private void requireRtnIsFree(String rtn, Long selfId) {
        if (rtn == null) return;
        supplierRepository.findFirstByRtn(rtn)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new APIException("El RTN " + rtn + " ya está registrado para el proveedor «"
                            + existing.getName() + "»"
                            + (existing.isActive() ? "." : ", que está desactivado: reactívelo en lugar de crear otro."));
                });
    }

    private void apply(Supplier supplier, SupplierDTO dto, String rtn) {
        String name = normalize(dto.getName());
        if (name == null) {
            throw new APIException("El nombre del proveedor es obligatorio.");
        }
        supplier.setName(name);
        supplier.setRtn(rtn);
        supplier.setPhone(normalize(dto.getPhone()));
        supplier.setEmail(normalize(dto.getEmail()));
        supplier.setAddress(normalize(dto.getAddress()));
    }

    private Supplier findSupplier(Long supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "supplierId", supplierId));
    }

    /** Recorta espacios; los campos en blanco quedan nulos. */
    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private SupplierDTO toDTO(Supplier supplier) {
        return new SupplierDTO(supplier.getId(), supplier.getName(), supplier.getRtn(), supplier.getPhone(),
                supplier.getEmail(), supplier.getAddress(), supplier.isActive());
    }
}
