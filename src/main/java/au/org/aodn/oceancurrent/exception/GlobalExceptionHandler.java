package au.org.aodn.oceancurrent.exception;

import au.org.aodn.oceancurrent.constant.ElasticsearchErrorType;
import au.org.aodn.oceancurrent.dto.ErrorResponse;
import co.elastic.clients.transport.TransportException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public GlobalExceptionHandler() {
        log.info("GlobalExceptionHandler initialized");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.info("Resource Not Found: {}", ex.getMessage());

        return new ErrorResponse(
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                List.of(ex.getMessage())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String > errorMessages = ex.getBindingResult().getAllErrors()
                .stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.toList());
        if (log.isDebugEnabled()) {
            errorMessages.forEach(message -> log.debug("Validation error detail: {}", message));
        }

        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                errorMessages);
    }

    @ExceptionHandler(InvalidProductException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidProductException(InvalidProductException ex) {
        // Create error response using your existing ErrorResponse class structure
        log.info("Product validation failed: {}", ex.getMessage());
        return new ErrorResponse(
                "Product validation failed",
                Collections.singletonList(ex.getMessage())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgumentException(IllegalArgumentException ex) {
        log.info("Illegal Argument: {}", ex.getMessage());

        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                List.of(ex.getMessage())
        );
    }

    @ExceptionHandler(ElasticsearchConnectionException.class)
    public ErrorResponse handleElasticsearchConnectionException(ElasticsearchConnectionException ex, HttpServletRequest request) {
        String message = String.format(
                "%s: Host URL '%s' - %s",
                ex.getErrorType().name(),
                ex.getHostUrl(),
                ex.getMessage()
        );

        HttpStatus status = ex.getHttpStatus();
        if (status.is5xxServerError()) {
            log.error(message, ex);
        } else {
            log.warn(message, ex);
        }

        request.setAttribute("org.springframework.web.servlet.HandlerMapping.errorStatus", ex.getHttpStatus());

        return new ErrorResponse(
                ex.getErrorType().getTitle(),
                List.of(ex.getMessage())
        );
    }

    @ExceptionHandler(TransportException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleTransportException(TransportException ex, HttpServletRequest request) {
        String errorReference = UUID.randomUUID().toString();
        String path = request.getRequestURI();

        log.error("Unhandled Elasticsearch transport error [ref:{}] for request to {}: {}",
                errorReference, path, ex.getMessage(), ex);

        String userMessage = String.format("%s Reference: %s",
                ElasticsearchErrorType.GENERAL_ERROR.getDetail(),
                errorReference);

        return new ErrorResponse(
                ElasticsearchErrorType.GENERAL_ERROR.getTitle(),
                List.of(userMessage)
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleException(Exception ex, HttpServletRequest httpRequest) {
        String errorReference = UUID.randomUUID().toString();

        String path = httpRequest.getRequestURI();
        String queryString = httpRequest.getQueryString();
        String fullPath = queryString != null ? path + "?" + queryString : "";
        log.error("Uncaught exception [ref:{}] for request to {}", errorReference, fullPath, ex);

        String userMessage = "An unexpected error occurred. Reference: " + errorReference;

        return new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                List.of(userMessage)
        );
    }
}
