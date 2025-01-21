package au.org.aodn.oceancurrent.exception;

public class RemoteFileException extends RuntimeException {
    public RemoteFileException(String message) {
        super(message);
    }

    public RemoteFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
