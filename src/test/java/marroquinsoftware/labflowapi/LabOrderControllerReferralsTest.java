package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.controller.v1.LabOrderController;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.ReferralDTO;
import marroquinsoftware.labflowapi.service.LabOrderService;
import marroquinsoftware.labflowapi.service.ReferralService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * El detalle de la orden (GET /orders/{id}) embebe las remisiones para ahorrarle
 * al front una 2.ª llamada a GET /orders/{id}/referrals (una invocación menos de
 * Cloudflare por apertura de la pantalla de más tráfico). Pero solo para quien
 * podría pedir ese endpoint: tiene la MISMA regla de permiso (ORDERS_VIEW o
 * ORDERS_REFER), así el ahorro no expone las remisiones a roles con solo
 * ORDERS_PRINT/ORDERS_ENTER_RESULTS, que también pueden abrir el detalle.
 */
class LabOrderControllerReferralsTest {

    private final LabOrderService labOrderService = mock(LabOrderService.class);
    private final ReferralService referralService = mock(ReferralService.class);

    private LabOrderController controller() {
        LabOrderController controller = new LabOrderController();
        ReflectionTestUtils.setField(controller, "labOrderService", labOrderService);
        ReflectionTestUtils.setField(controller, "referralService", referralService);
        return controller;
    }

    private Authentication authWith(String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken("tester", "x", granted);
    }

    @Test
    void embedsReferralsWhenCallerCanViewThem() {
        LabOrderDTO order = new LabOrderDTO();
        when(labOrderService.getOrderById(7L)).thenReturn(order);
        List<ReferralDTO> referrals = List.of(new ReferralDTO());
        when(referralService.getReferralsByOrder(7L)).thenReturn(referrals);

        ResponseEntity<LabOrderDTO> response = controller().getOrderById(7L, authWith("ORDERS_VIEW"));

        // El detalle trae las remisiones embebidas: el front las lee de order.referrals
        // en vez de pedir GET /orders/{id}/referrals.
        assertSame(referrals, response.getBody().getReferrals());
        verify(referralService).getReferralsByOrder(7L);
    }

    @Test
    void embedsReferralsForReferrerRole() {
        LabOrderDTO order = new LabOrderDTO();
        when(labOrderService.getOrderById(7L)).thenReturn(order);
        when(referralService.getReferralsByOrder(7L)).thenReturn(List.of(new ReferralDTO()));

        ResponseEntity<LabOrderDTO> response = controller().getOrderById(7L, authWith("ORDERS_REFER"));

        assertNotNull(response.getBody().getReferrals());
        verify(referralService).getReferralsByOrder(7L);
    }

    @Test
    void doesNotEmbedReferralsForPrintOrResultsOnlyRoles() {
        LabOrderDTO order = new LabOrderDTO();
        when(labOrderService.getOrderById(7L)).thenReturn(order);

        ResponseEntity<LabOrderDTO> response = controller()
                .getOrderById(7L, authWith("ORDERS_PRINT", "ORDERS_ENTER_RESULTS"));

        // Sin permiso para ver remisiones: no se embeben (quedan null) y ni siquiera
        // se consulta el servicio. El front cae a su propia llamada, igual que hoy.
        assertNull(response.getBody().getReferrals());
        verify(referralService, never()).getReferralsByOrder(anyLong());
    }
}
