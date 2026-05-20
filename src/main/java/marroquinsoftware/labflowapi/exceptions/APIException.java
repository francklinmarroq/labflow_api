package marroquinsoftware.labflowapi.exceptions;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class APIException extends RuntimeException {
    private static long serialVersionUID = 1L;

    public APIException(String message) {
        super(message);
    }
}
