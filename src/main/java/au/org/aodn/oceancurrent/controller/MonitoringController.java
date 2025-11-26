package au.org.aodn.oceancurrent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for internal monitoring and health check endpoints.
 * Used by external monitoring systems (e.g., NewRelic) to verify application health.
 * In production/edge environments, requires AWS IAM authentication.
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
                    "This endpoint is used for testing monitoring alerts and system health checks. " +
                    "In production/edge environments, requires AWS IAM authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fatal log generated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - AWS IAM authentication required (prod/edge only)")
    })
    public ResponseEntity<Map<String, Object>> triggerFatalLog() {
        Instant timestamp = Instant.now();

        // Generate fatal log that will be caught by NewRelic monitoring
        log.error("[FATAL] Monitoring health check endpoint triggered at {}", timestamp);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Fatal log generated successfully for monitoring purposes");
        response.put("timestamp", timestamp.toString());
        response.put("logLevel", "FATAL");

        log.info("Monitoring endpoint called successfully at {}", timestamp);

        return ResponseEntity.ok(response);
    }
}
