package marroquinsoftware.labflowapi.model;

/**
 * Catálogo fijo de permisos (vistas y acciones) que un rol puede otorgar.
 * La API es la fuente de verdad: el frontend pinta la pantalla de roles con
 * el módulo y la etiqueta que se exponen en {@code GET /api/v1/permissions}.
 */
public enum Permission {

    ORDERS_VIEW("Órdenes", "Ver órdenes"),
    ORDERS_CREATE("Órdenes", "Crear órdenes"),
    ORDERS_ENTER_RESULTS("Órdenes", "Ingresar resultados"),
    ORDERS_PRINT("Órdenes", "Imprimir órdenes"),
    ORDERS_REFER("Órdenes", "Remitir exámenes"),
    ORDERS_DELETE("Órdenes", "Cancelar órdenes"),

    QUOTES_VIEW("Cotizaciones", "Ver cotizaciones"),
    QUOTES_CREATE("Cotizaciones", "Crear cotizaciones"),
    QUOTES_DELETE("Cotizaciones", "Eliminar cotizaciones"),

    PATIENTS_VIEW("Pacientes", "Ver pacientes"),
    PATIENTS_CREATE("Pacientes", "Crear pacientes"),
    PATIENTS_EDIT("Pacientes", "Editar pacientes"),
    PATIENTS_DELETE("Pacientes", "Eliminar pacientes"),

    CATALOG_VIEW("Catálogo", "Ver el catálogo"),
    CATALOG_CREATE("Catálogo", "Crear entradas del catálogo"),
    CATALOG_EDIT("Catálogo", "Editar entradas del catálogo"),
    CATALOG_DELETE("Catálogo", "Eliminar entradas del catálogo"),

    LAB_SETTINGS_VIEW("Configuración", "Ver datos del laboratorio"),
    LAB_SETTINGS_EDIT("Configuración", "Editar datos del laboratorio"),

    USERS_VIEW("Usuarios y roles", "Ver usuarios"),
    USERS_MANAGE("Usuarios y roles", "Gestionar usuarios"),
    ROLES_VIEW("Usuarios y roles", "Ver roles"),
    ROLES_MANAGE("Usuarios y roles", "Gestionar roles");

    private final String module;
    private final String label;

    Permission(String module, String label) {
        this.module = module;
        this.label = label;
    }

    public String getModule() {
        return module;
    }

    public String getLabel() {
        return label;
    }
}
