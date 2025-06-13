package au.org.aodn.oceancurrent.exception;

public class S3ServiceException extends RuntimeException {
    public S3ServiceException(String message) {
        super(message);
    }

    public S3ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
