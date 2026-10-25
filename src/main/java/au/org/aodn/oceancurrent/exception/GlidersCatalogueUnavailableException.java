package au.org.aodn.oceancurrent.exception;

public class GlidersCatalogueUnavailableException extends RuntimeException {
    public GlidersCatalogueUnavailableException(Throwable cause) {
        super("Gliders mission catalogue is currently unavailable. Please try again later.", cause);
    }
}
