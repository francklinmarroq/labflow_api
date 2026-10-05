package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.SupplierDTO;
import marroquinsoftware.labflowapi.payload.SupplierResponse;

/** Catálogo de proveedores: se desactivan, nunca se borran. */
public interface SupplierService {

    SupplierResponse getSuppliers(Integer pageNumber, Integer pageSize, String sortBy, String sortDir,
                                  String search, Boolean active);

    SupplierDTO getSupplier(Long supplierId);

    SupplierDTO createSupplier(SupplierDTO dto);

    SupplierDTO updateSupplier(Long supplierId, SupplierDTO dto);

    SupplierDTO setActive(Long supplierId, boolean active);
}
