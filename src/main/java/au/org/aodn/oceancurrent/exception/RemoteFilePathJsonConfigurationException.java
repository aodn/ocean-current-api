package au.org.aodn.oceancurrent.exception;

public class RemoteFilePathJsonConfigurationException extends RuntimeException {
    public RemoteFilePathJsonConfigurationException(String message) {
        super(message);
    }

    public RemoteFilePathJsonConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
