package au.org.aodn.oceancurrent.exception;

import au.org.aodn.oceancurrent.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public GlobalExceptionHandler() {
        log.info("GlobalExceptionHandler initialized");
    }

    @ExceptionHandler(value = {ResourceNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.info("Resource Not Found: {}", ex.getMessage());

        return new ErrorResponse(
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                List.of(ex.getMessage())
        );
    }

    @ExceptionHandler(value = {MethodArgumentNotValidException.class})
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
