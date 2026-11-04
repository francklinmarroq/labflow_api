package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.controller.v1.QuoteController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La vista previa del descuento por edad la usan la cotización y Nueva orden:
 * quien solo crea órdenes también tiene que poder consultarla, o su resumen se
 * queda sin el descuento. Se lee de la anotación porque la suite no levanta la
 * capa de seguridad (igual que PurchasePermissionsTest).
 */
class AgeDiscountPreviewPermissionTest {

    @Test
    void ordersCreatorsCanPreviewTheAgeDiscount() throws Exception {
        String rule = QuoteController.class.getMethod("previewDiscount", Integer.class)
                .getAnnotation(PreAuthorize.class).value();
        for (String permission : new String[] {"QUOTES_CREATE", "QUOTES_VIEW", "ORDERS_CREATE"}) {
            assertTrue(rule.contains("'" + permission + "'"), "falta " + permission + " en " + rule);
        }
    }
}
