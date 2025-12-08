package au.org.aodn.oceancurrent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/monitoring")
@Slf4j
@Tag(name = "Monitoring", description = "Internal monitoring endpoints for health checks and alerting. Requires API key authentication.")
public class MonitoringController {

    @PostMapping("/alert")
    @Operation(
            summary = "Report failures or errors from external tasks",
            description = "Generates a FATAL log entry when called with an error message. Used by external tasks like cron jobs to report failures so monitoring systems can detect and alert on them.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    public ResponseEntity<Map<String, Object>> reportFailure(
            @Parameter(description = "The error message describing what went wrong")
            @RequestParam(required = false, defaultValue= "Monitoring alert triggered") String message,
            @Parameter(description = "Optional context or job name")
            @RequestParam(required = false) String source) {

        ZonedDateTime timestamp = ZonedDateTime.now();

        if (source != null && !source.isEmpty()) {
            log.error("[FATAL]: [{}] {}", source, message);
        } else {
            log.error("[FATAL]: {}", message);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "logged");
        response.put("timestamp", timestamp.toString());
        response.put("message", "Failure logged successfully");

        log.info("Monitoring alert reported at {} with message '{}'", timestamp, message);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/ping")
    @Operation(
            summary = "Simple ping endpoint to verify API is reachable",
            description = "Returns 200 OK if the API is up and running. Does not generate any logs.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    public ResponseEntity<Map<String, String>> ping() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        response.put("timestamp", ZonedDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
