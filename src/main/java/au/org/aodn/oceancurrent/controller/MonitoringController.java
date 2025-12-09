package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.dto.MonitoringRequest;
import au.org.aodn.oceancurrent.dto.MonitoringResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Controller for internal monitoring and health check endpoints.
 * Used by external monitoring systems (e.g., NewRelic) to verify application health.
 * In production/edge environments, requires EC2 instance authentication.
 */
@RestController
@RequestMapping("/monitoring")
@Slf4j
@Tag(name = "Monitoring", description = "Internal monitoring endpoints for health checks and alerting")
public class MonitoringController {

    @PostMapping("/fatal-log")
    @Operation(
            summary = "Generate a fatal log entry for monitoring",
            description = "Triggers a fatal error log that can be detected by external monitoring systems like NewRelic. " +
                    "Accepts an optional request body with custom error message, source, and context. " +
                    "If no error message is provided, a default message will be used. " +
                    "In production/edge environments, requires EC2 instance authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fatal log generated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorised - EC2 instance authentication required (prod/edge only)")
    })
    public ResponseEntity<MonitoringResponse> triggerFatalLog(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Optional monitoring request with custom error message")
            @RequestBody(required = false) MonitoringRequest request) {

        Instant timestamp = Instant.now();

        String errorMessage;
        if (request != null && request.getErrorMessage() != null && !request.getErrorMessage().trim().isEmpty()) {
            errorMessage = request.getErrorMessage();
        } else {
            errorMessage = "Monitoring health check endpoint triggered";
        }

        StringBuilder logContext = new StringBuilder();
        logContext.append(errorMessage);

        if (request != null) {
            if (request.getSource() != null && !request.getSource().trim().isEmpty()) {
                logContext.append(" [source=").append(request.getSource()).append("]");
            }
            if (request.getContext() != null && !request.getContext().trim().isEmpty()) {
                logContext.append(" [context=").append(request.getContext()).append("]");
            }
        }

        // Generate fatal log that will be caught by NewRelic monitoring
        log.error("[FATAL] Monitoring log triggered at {} with message: {}", timestamp, logContext);

        MonitoringResponse response = MonitoringResponse.builder()
                .status("success")
                .message("Fatal log generated successfully for monitoring purposes")
                .timestamp(timestamp.toString())
                .logLevel("FATAL")
                .loggedError(logContext.toString())
                .build();

        log.info("Monitoring endpoint called successfully at {} with message: {}", timestamp, errorMessage);

        return ResponseEntity.ok(response);
    }
}
