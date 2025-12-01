package au.org.aodn.oceancurrent.security;

import au.org.aodn.oceancurrent.configuration.MonitoringSecurityProperties;
import au.org.aodn.oceancurrent.dto.MonitoringRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Authentication filter for internal monitoring endpoints.
 * Only active in production and edge environments.
 *
 * Validates EC2 instance identity using:
 * 1. Instance identity document from EC2 metadata service
 * 2. PKCS7 signature to prove authenticity
 * 3. Whitelist of authorised instance IDs
 *
 * This provides defense-in-depth:
 * - PKCS7 signature proves the document is authentic and from AWS
 * - Instance ID proves it came from a specific EC2 instance
 * - Whitelist prevents unauthorised instances from accessing the endpoint
 */
@Component
@Profile({"prod", "edge"})
@Slf4j
@RequiredArgsConstructor
public class Ec2InstanceAuthenticationFilter extends OncePerRequestFilter {

    private static final String MONITORING_PATH = "/api/v1/monitoring";

    private final AwsInstanceIdentityValidator instanceIdentityValidator;
    private final ObjectMapper objectMapper;
    private final MonitoringSecurityProperties monitoringSecurityProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Only filter monitoring endpoints
        String path = request.getRequestURI();
        return !path.startsWith(MONITORING_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        String clientIp = getClientIp(request);

        try {
            MonitoringRequest monitoringRequest;

            try {
                monitoringRequest = parseMonitoringRequest(request);
            } catch (Exception e) {
                log.warn("[MONITORING-AUTH-FAILED] Invalid JSON in request body | Path={} | IP={} | Error={}",
                        requestPath, clientIp, e.getMessage());
                sendUnauthorisedResponse(response, "Invalid request body format");
                return;
            }

            String instanceId = monitoringRequest.getInstanceId();
            String document = monitoringRequest.getDocument();
            String pkcs7 = monitoringRequest.getPkcs7();

            // Validate required fields are present
            if (instanceId == null || instanceId.isEmpty()) {
                log.warn("[MONITORING-AUTH-FAILED] Missing instanceId in request body | Path={} | IP={}",
                        requestPath, clientIp);
                sendUnauthorisedResponse(response, "Instance ID required in request body");
                return;
            }

            if (document == null || document.isEmpty()) {
                log.warn("[MONITORING-AUTH-FAILED] Missing document in request body | Path={} | IP={} | InstanceId={}",
                        requestPath, clientIp, instanceId);
                sendUnauthorisedResponse(response, "Instance identity document required");
                return;
            }

            if (pkcs7 == null || pkcs7.isEmpty()) {
                log.warn("[MONITORING-AUTH-FAILED] Missing pkcs7 signature in request body | Path={} | IP={} | InstanceId={}",
                        requestPath, clientIp, instanceId);
                sendUnauthorisedResponse(response, "PKCS7 signature required");
                return;
            }

            AwsInstanceIdentityValidator.ValidationResult validationResult =
                instanceIdentityValidator.validate(document, pkcs7, instanceId);

            if (!validationResult.isValid()) {
                log.warn("[MONITORING-AUTH-FAILED] Instance identity validation failed | InstanceId={} | IP={} | Reason={}",
                        instanceId, clientIp, validationResult.getErrorMessage());
                sendUnauthorisedResponse(response, "Invalid instance identity: " + validationResult.getErrorMessage());
                return;
            }

            Set<String> authorisedIds = new HashSet<>(monitoringSecurityProperties.getAuthorizedInstanceIds());
            if (!authorisedIds.contains(instanceId)) {
                log.warn("[MONITORING-AUTH-FAILED] Unauthorised instance ID | InstanceId={} | IP={}",
                        instanceId, clientIp);
                sendUnauthorisedResponse(response, "Instance not authorised");
                return;
            }

            log.info("[MONITORING-AUTH-SUCCESS] EC2 instance authenticated | InstanceId={} | IP={} | Path={}",
                    instanceId, clientIp, requestPath);

            request.setAttribute("ec2_instance_id", instanceId);
            request.setAttribute("ec2_authenticated", true);
            request.setAttribute("monitoring_request", monitoringRequest);

        } catch (IOException e) {
            log.error("[MONITORING-AUTH-ERROR] IOException during authentication | Path={} | Error={}",
                    requestPath, e.getMessage());
            sendUnauthorisedResponse(response, "Authentication error");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Parse request body into MonitoringRequest DTO.
     * Note: This reads the input stream, which can only be read once.
     * We use ContentCachingRequestWrapper to enable multiple reads.
     *
     * @param request the HTTP request
     * @return parsed MonitoringRequest object
     * @throws IOException if reading the request body fails
     */
    private MonitoringRequest parseMonitoringRequest(HttpServletRequest request) throws IOException {
        if (!(request instanceof ContentCachingRequestWrapper)) {
            request = new ContentCachingRequestWrapper(request);
        }

        ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
        String body = StreamUtils.copyToString(wrapper.getInputStream(), StandardCharsets.UTF_8);

        return objectMapper.readValue(body, MonitoringRequest.class);
    }

    /**
     * Get client IP address from headers or remote address.
     * Checks X-Forwarded-For header first (for requests through load balancer).
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Send 401 Unauthorised response with JSON error message.
     */
    private void sendUnauthorisedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORISED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"error\": \"Unauthorised\", \"message\": \"%s\"}",
                message
        ));
    }
}
