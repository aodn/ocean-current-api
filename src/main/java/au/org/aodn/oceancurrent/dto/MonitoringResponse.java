package au.org.aodn.oceancurrent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from monitoring endpoint")
public class MonitoringResponse {

    @Schema(description = "Status of the operation", example = "success")
    private String status;

    @Schema(description = "Response message", example = "Fatal log generated successfully for monitoring purposes")
    private String message;

    @Schema(description = "Timestamp of the log entry", example = "2025-11-26T12:34:56.789Z")
    private String timestamp;

    @Schema(description = "Log level used", example = "FATAL")
    private String logLevel;

    @Schema(description = "The error message that was logged (if provided)", example = "Cron job failed: file scan timeout while generating JSON index")
    private String loggedError;
}
