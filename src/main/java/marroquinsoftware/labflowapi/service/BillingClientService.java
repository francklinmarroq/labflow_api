package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.BillingClientDTO;
import marroquinsoftware.labflowapi.payload.BillingClientResponse;

public interface BillingClientService {
    BillingClientResponse getAllBillingClients(Integer pageNumber, Integer pageSize, String sortBy, String sortDir);
    BillingClientDTO getBillingClient(Long id);
    BillingClientDTO createBillingClient(BillingClientDTO dto);
    BillingClientDTO updateBillingClient(BillingClientDTO dto, Long id);
    BillingClientDTO deleteBillingClient(Long id);
}
