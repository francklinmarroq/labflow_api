package marroquinsoftware.labflowapi.exceptions;

import marroquinsoftware.labflowapi.payload.APIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Traduce toda excepción que llegue al usuario a un mensaje en español que le
 * diga qué pasó y qué hacer. Para errores internos (5xx) se genera un código de
 * soporte (ERR-XXXXXXXX) que se incluye en la respuesta y en el log junto al
 * stack trace, de modo que el usuario pueda reportarlo y soporte pueda ubicar
 * el error exacto sin pedirle más datos.
 */
@RestControllerAdvice
public class MyGlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyGlobalExceptionHandler.class);

    /** Código corto y único para correlacionar la respuesta al usuario con el log. */
    private String newSupportCode() {
        return "ERR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private ResponseEntity<APIResponse> respond(String message, HttpStatus status) {
        return new ResponseEntity<>(new APIResponse(message, false), status);
    }

    // ---------- Errores esperados de negocio (4xx) ----------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> myMethodArgumentNotValidException(MethodArgumentNotValidException e){
        Map<String, String> response = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(err -> {
           String fieldName = ((FieldError)err).getField();
           String message = err.getDefaultMessage();
           response.put(fieldName, message);
        });
        return new ResponseEntity<Map<String, String>>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<APIResponse> myResourceNotFoundException(ResourceNotFoundException e) {
        return respond(e.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(APIException.class)
    public ResponseEntity<APIResponse> myAPIException(APIException e) {
        return respond(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    // El body no se pudo deserializar (JSON malformado, valor de enum desconocido,
    // tipo incompatible, etc.). El detalle técnico va al log; al usuario se le da
    // un mensaje neutro porque este error viene del cliente, no de sus datos.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<APIResponse> myHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        LOGGER.warn("Solicitud ilegible: {}", e.getMostSpecificCause().getMessage());
        return respond("La solicitud enviada tiene un formato inválido y no se pudo procesar. "
                + "Intente de nuevo; si el problema persiste, reporte a soporte lo que estaba haciendo.",
                HttpStatus.BAD_REQUEST);
    }

    // Un parámetro de la URL no tiene el tipo esperado (ej. texto donde va un número).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<APIResponse> myMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        return respond("El valor '" + e.getValue() + "' no es válido para el campo '" + e.getName() + "'.",
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<APIResponse> myMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        return respond("Falta el parámetro requerido '" + e.getParameterName() + "'.", HttpStatus.BAD_REQUEST);
    }

    // sortBy apunta a un campo que no existe en la entidad.
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<APIResponse> myPropertyReferenceException(PropertyReferenceException e) {
        return respond("El criterio de ordenamiento '" + e.getPropertyName() + "' no es válido.",
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<APIResponse> myNoResourceFoundException(NoResourceFoundException e) {
        return respond("La página o recurso solicitado no existe.", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<APIResponse> myHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return respond("La operación solicitada no está disponible para este recurso.",
                HttpStatus.METHOD_NOT_ALLOWED);
    }

    // @PreAuthorize denegó la acción (incluye AuthorizationDeniedException).
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<APIResponse> myAccessDeniedException(AccessDeniedException e) {
        return respond("No tiene permisos para realizar esta acción. "
                + "Si cree que debería tenerlos, contacte al administrador de su laboratorio.",
                HttpStatus.FORBIDDEN);
    }

    // ---------- Conflictos de datos y concurrencia (409) ----------

    // La base de datos rechazó la operación por una restricción. Se distingue el
    // caso más común para el usuario: borrar algo que está en uso, o duplicar
    // un valor único que la validación de negocio no alcanzó a detectar.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIResponse> myDataIntegrityViolationException(DataIntegrityViolationException e) {
        String code = newSupportCode();
        LOGGER.warn("[{}] Violación de integridad de datos", code, e);
        String detail = e.getMostSpecificCause() != null && e.getMostSpecificCause().getMessage() != null
                ? e.getMostSpecificCause().getMessage().toLowerCase(Locale.ROOT)
                : "";
        String message;
        if (detail.contains("foreign key") || detail.contains("referential integrity")
                || detail.contains("violates foreign key")) {
            message = "No se puede completar la operación porque el registro está en uso por otros datos "
                    + "(por ejemplo, órdenes o resultados que dependen de él). "
                    + "Elimine o modifique primero esos registros.";
        } else if (detail.contains("unique") || detail.contains("duplicate")) {
            message = "Ya existe un registro con esos datos. Verifique los valores e intente de nuevo.";
        } else if (detail.contains("null")) {
            message = "Falta información obligatoria para guardar el registro. Revise el formulario.";
        } else {
            message = "Los datos entran en conflicto con información existente y no se pudieron guardar. "
                    + "Si el problema persiste, reporte a soporte el código " + code + ".";
        }
        return respond(message, HttpStatus.CONFLICT);
    }

    // Otro usuario modificó el mismo registro entre que este usuario lo leyó y lo guardó.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<APIResponse> myOptimisticLockingFailureException(OptimisticLockingFailureException e) {
        return respond("Otro usuario modificó este registro al mismo tiempo. "
                + "Recargue la página para ver los cambios e intente de nuevo.", HttpStatus.CONFLICT);
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, CannotAcquireLockException.class, QueryTimeoutException.class})
    public ResponseEntity<APIResponse> myLockAcquisitionException(Exception e) {
        String code = newSupportCode();
        LOGGER.warn("[{}] Bloqueo o timeout en la base de datos", code, e);
        return respond("El sistema está procesando otra operación sobre estos datos. "
                + "Espere unos segundos e intente de nuevo. Si el problema persiste, "
                + "reporte a soporte el código " + code + ".", HttpStatus.CONFLICT);
    }

    // ---------- Infraestructura y errores inesperados (5xx) ----------

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    public ResponseEntity<APIResponse> myDatabaseUnavailableException(Exception e) {
        String code = newSupportCode();
        LOGGER.error("[{}] No se pudo conectar con la base de datos", code, e);
        return respond("No se pudo conectar con la base de datos. Intente de nuevo en unos minutos. "
                + "Si el problema persiste, reporte a soporte el código " + code + ".",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    // Red de seguridad: cualquier error no contemplado. Nunca se expone el detalle
    // técnico al usuario; el código de soporte permite ubicarlo en el log.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse> myUnexpectedException(Exception e) {
        String code = newSupportCode();
        LOGGER.error("[{}] Error inesperado", code, e);
        return respond("Ocurrió un error inesperado en el sistema. Intente de nuevo. "
                + "Si el problema persiste, reporte a soporte el código " + code + ".",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
