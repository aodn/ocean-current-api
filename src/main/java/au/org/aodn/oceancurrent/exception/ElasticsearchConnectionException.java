package au.org.aodn.oceancurrent.exception;

import au.org.aodn.oceancurrent.constant.ElasticsearchErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public class ElasticsearchConnectionException extends RuntimeException {
    private final String hostUrl;
    private final ElasticsearchErrorType errorType;

    public ElasticsearchConnectionException(String hostUrl, ElasticsearchErrorType errorType, String message, Throwable cause) {
      super(message, cause);
      this.hostUrl = hostUrl;
      this.errorType = errorType;
    }

    public HttpStatus getHttpStatus() {
      return errorType.getStatus();
    }
}
