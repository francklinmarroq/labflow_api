package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.ReferralDTO;
import marroquinsoftware.labflowapi.payload.ReferralRequest;

import java.util.List;

public interface ReferralService {
    ReferralDTO createReferral(Long orderId, ReferralRequest request);
    List<ReferralDTO> getReferralsByOrder(Long orderId);
    ReferralDTO getReferral(Long referralId);
    List<String> getDestinationLabNames();
    void deleteReferral(Long referralId);
}
