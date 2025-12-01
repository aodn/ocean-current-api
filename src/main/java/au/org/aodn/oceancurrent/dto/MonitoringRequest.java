package au.org.aodn.oceancurrent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for monitoring endpoint.
 * Allows callers to provide custom error messages for logging with EC2 instance authentication.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for monitoring endpoint to generate fatal logs")
public class MonitoringRequest {

    @Schema(
            description = "EC2 instance ID making the request. Fetched from EC2 metadata service.",
            example = "i-0123456789abcdef0",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Instance ID is required")
    private String instanceId;

    @Schema(
            description = "Instance identity document JSON from EC2 metadata service. " +
                    "Fetch from: http://169.254.169.254/latest/dynamic/instance-identity/document",
            example = "{\"instanceId\":\"i-0123456789abcdef0\",\"region\":\"ap-southeast-2\",...}",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Instance identity document is required")
    private String document;

    @Schema(
            description = "PKCS7 signature of the instance identity document from EC2 metadata service. " +
                    "Fetch from: http://169.254.169.254/latest/dynamic/instance-identity/pkcs7 " +
                    "This signature proves the document is authentic and from AWS.",
            example = "MIAGCSqGSIb3DQEHAqCAMIACAQExCzAJBgUrDgMCGgUAMIAGCSqGSIb3DQEHAaCAJIAEggHc...",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "PKCS7 signature is required")
    private String pkcs7;

    @Schema(
            description = "Timestamp when the request was created (ISO 8601 format)",
            example = "2025-11-28T12:00:00Z"
    )
    private String timestamp;

    @Schema(
            description = "Custom error message to be logged. If empty or null, a default message will be used.",
            example = "Cron job failed: file scan timeout while generating JSON index"
    )
    private String errorMessage;

    @Schema(
            description = "Optional source identifier (e.g., script name, job name)",
            example = "daily-data-sync-job"
    )
    private String source;

    @Schema(
            description = "Optional additional context or metadata",
            example = "exit_code=1, last_run=2025-11-26T12:00:00Z"
    )
    private String context;
}
