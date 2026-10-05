package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.controller.v1.PurchaseController;
import marroquinsoftware.labflowapi.controller.v1.PurchaseReportController;
import marroquinsoftware.labflowapi.controller.v1.SupplierController;
import marroquinsoftware.labflowapi.model.Permission;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Los permisos de compras sobre los endpoints: consultar pide los de vista (o
 * los de gestión, que los incluyen) y registrar, anular y pagar piden el de
 * gestión. Se lee de las anotaciones porque la suite no levanta la capa de
 * seguridad; que Spring las aplique ya está cubierto por el resto de la app.
 */
class PurchasePermissionsTest {

    @Test
    void permissionsExistInTheComprasModule() {
        for (Permission p : List.of(Permission.SUPPLIERS_VIEW, Permission.SUPPLIERS_MANAGE,
                Permission.PURCHASES_VIEW, Permission.PURCHASES_MANAGE)) {
            assertEquals("Compras", p.getModule());
        }
    }

    @Test
    void readsNeedAViewPermissionAndWritesNeedManage() {
        for (Class<?> controller : List.of(SupplierController.class, PurchaseController.class,
                PurchaseReportController.class)) {
            for (Method method : controller.getDeclaredMethods()) {
                PreAuthorize rule = method.getAnnotation(PreAuthorize.class);
                if (rule == null) continue;
                String expression = rule.value();
                boolean isRead = method.isAnnotationPresent(GetMapping.class);
                String where = controller.getSimpleName() + "." + method.getName() + ": " + expression;
                if (isRead) {
                    assertTrue(expression.contains("_VIEW"), "una consulta debe aceptar un permiso de vista — " + where);
                } else {
                    assertTrue(expression.startsWith("hasAuthority(") && expression.contains("_MANAGE"),
                            "una escritura debe pedir solo el permiso de gestión — " + where);
                }
            }
        }
    }

    @Test
    void everyEndpointIsGuarded() {
        for (Class<?> controller : List.of(SupplierController.class, PurchaseController.class,
                PurchaseReportController.class)) {
            assertTrue(Arrays.stream(controller.getDeclaredMethods())
                            .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                            .allMatch(m -> m.isAnnotationPresent(PreAuthorize.class)),
                    controller.getSimpleName() + " tiene un endpoint sin @PreAuthorize");
        }
    }
}
