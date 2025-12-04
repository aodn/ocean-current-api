package au.org.aodn.oceancurrent.exception;

public class ReindexValidationException extends RuntimeException {
    public ReindexValidationException(String message) {
        super(message);
    }

    public ReindexValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
