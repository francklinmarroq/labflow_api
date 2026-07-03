package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.payload.ReferralDTO;
import marroquinsoftware.labflowapi.payload.ReferralRequest;
import marroquinsoftware.labflowapi.service.ReferralService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ReferralController {

    @Autowired
    private ReferralService referralService;

    @PostMapping("/orders/{orderId}/referrals")
    @PreAuthorize("hasAuthority('ORDERS_REFER')")
    public ResponseEntity<ReferralDTO> createReferral(
            @PathVariable Long orderId,
            @Valid @RequestBody ReferralRequest request) {
        return new ResponseEntity<>(referralService.createReferral(orderId, request), HttpStatus.CREATED);
    }

    @GetMapping("/orders/{orderId}/referrals")
    @PreAuthorize("hasAnyAuthority('ORDERS_VIEW','ORDERS_REFER')")
    public ResponseEntity<List<ReferralDTO>> getReferralsByOrder(@PathVariable Long orderId) {
        return new ResponseEntity<>(referralService.getReferralsByOrder(orderId), HttpStatus.OK);
    }

    @GetMapping("/referrals/{referralId}")
    @PreAuthorize("hasAnyAuthority('ORDERS_VIEW','ORDERS_REFER')")
    public ResponseEntity<ReferralDTO> getReferral(@PathVariable Long referralId) {
        return new ResponseEntity<>(referralService.getReferral(referralId), HttpStatus.OK);
    }

    @GetMapping("/referrals/destination-labs")
    @PreAuthorize("hasAuthority('ORDERS_REFER')")
    public ResponseEntity<List<String>> getDestinationLabs() {
        return new ResponseEntity<>(referralService.getDestinationLabNames(), HttpStatus.OK);
    }

    @DeleteMapping("/referrals/{referralId}")
    @PreAuthorize("hasAuthority('ORDERS_REFER')")
    public ResponseEntity<Void> deleteReferral(@PathVariable Long referralId) {
        referralService.deleteReferral(referralId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
