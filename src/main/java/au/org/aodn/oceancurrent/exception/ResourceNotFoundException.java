package au.org.aodn.oceancurrent.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
    public ResourceNotFoundException() {
        super("Resource not found");
    }

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(String.format("%s not found with identifier: %s", resourceType, identifier));
    }
}
