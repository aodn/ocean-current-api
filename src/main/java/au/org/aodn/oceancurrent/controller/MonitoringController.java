package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.dto.MonitoringAlertRequest;
import au.org.aodn.oceancurrent.dto.MonitoringResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;

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
    public ResponseEntity<MonitoringResponse> reportFailure(@RequestBody MonitoringAlertRequest request) {

        ZonedDateTime timestamp = ZonedDateTime.now();

        String message = StringUtils.hasText(request.getMessage()) ? request.getMessage() : "Monitoring alert triggered";
        String source = request.getSource();

        if (StringUtils.hasText(source)) {
            log.error("[FATAL]: [{}] {}", source, message);
        } else {
            log.error("[FATAL]: {}", message);
        }

        MonitoringResponse response = new MonitoringResponse("logged", timestamp, "Failure logged successfully");

        log.info("Monitoring alert reported at {} with message '{}'", timestamp, message);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/ping")
    @Operation(
            summary = "Simple ping endpoint to verify API is reachable",
            description = "Returns 200 OK if the API is up and running. Does not generate any logs.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    public ResponseEntity<MonitoringResponse> ping() {
        MonitoringResponse response = new MonitoringResponse("ok", ZonedDateTime.now(), "API is reachable");
        return ResponseEntity.ok(response);
    }
}
