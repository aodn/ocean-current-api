package au.org.aodn.oceancurrent.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ElasticsearchErrorType {
    CONNECTION_ERROR(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Elasticsearch service unavailable",
            "Unable to connect to Elasticsearch. The service may be down or the configuration is incorrect."),
    AUTHENTICATION_ERROR(
            HttpStatus.UNAUTHORIZED,
            "Elasticsearch authentication failed",
            "Failed to authenticate with Elasticsearch. The API key may be incorrect or expired."),
    ENDPOINT_ERROR(
            HttpStatus.NOT_FOUND,
            "Elasticsearch endpoint not found",
            "The specified Elasticsearch endpoint could not be found. Check the URL path."),
    GENERAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Elasticsearch operation failed",
            "An error occurred while communicating with Elasticsearch.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
