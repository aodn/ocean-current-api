package au.org.aodn.oceancurrent.util;

import java.net.MalformedURLException;
import java.net.URL;

public class UrlUtils {
    private UrlUtils() {}

    /**
     * Masks sensitive parts of URLs for logging.
     * Keeps protocol, host, and port but removes paths, credentials, and query params.
     *
     * @param url The URL to mask
     * @return A masked version of the URL containing only protocol, host and port
     */
    public static String maskSensitiveUrl(String url) {
        if (url == null || url.isBlank()) {
            return "[empty-url]";
        }

        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getProtocol() + "://" + parsedUrl.getHost() +
                    (parsedUrl.getPort() > 0 ? ":" + parsedUrl.getPort() : "");
        } catch (MalformedURLException e) {
            // If we can't parse it, return a placeholder to avoid exposing potential secrets
            return "[malformed-url]";
        }
    }
}
