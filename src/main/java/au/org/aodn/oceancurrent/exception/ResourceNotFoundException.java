package au.org.aodn.oceancurrent.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException() {
        super("Resource not found");
    }
    public ResourceNotFoundException(String resource) {
        super(resource + " not found");
    }
}
