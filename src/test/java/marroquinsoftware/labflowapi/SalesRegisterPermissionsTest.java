package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.controller.v1.AnalyticsReportController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El registro de ventas detallado pide el mismo permiso que el resumen de
 * ventas. Se lee de las anotaciones porque la suite no levanta la capa de
 * seguridad, igual que en {@link PurchasePermissionsTest}.
 */
class SalesRegisterPermissionsTest {

    @Test
    void theRegisterNeedsTheSamePermissionAsTheSummary() throws NoSuchMethodException {
        Method detail = AnalyticsReportController.class.getMethod("getSalesDetail", LocalDate.class, LocalDate.class);
        Method summary = AnalyticsReportController.class.getMethod("getSales", LocalDate.class, LocalDate.class, String.class);

        assertEquals("hasAuthority('REPORTS_VIEW')", detail.getAnnotation(PreAuthorize.class).value());
        assertEquals(summary.getAnnotation(PreAuthorize.class).value(), detail.getAnnotation(PreAuthorize.class).value());
        assertArrayEquals(new String[]{"/sales/detail"}, detail.getAnnotation(GetMapping.class).value());
    }
}
