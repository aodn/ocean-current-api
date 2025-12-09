package au.org.aodn.oceancurrent.security;

import au.org.aodn.oceancurrent.configuration.MonitoringSecurityProperties;
import au.org.aodn.oceancurrent.dto.ErrorResponse;
import au.org.aodn.oceancurrent.dto.MonitoringRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Authentication filter for internal monitoring endpoints.
 * Only active in production and edge environments.
 * <p>
 * Validates EC2 instance identity using:
 * 1. Instance identity document from EC2 metadata service
 * 2. PKCS7 signature to prove authenticity
 * 3. Whitelist of authorised instance IDs
 * <p>
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

    private final Ec2InstanceIdentityValidator instanceIdentityValidator;
    private final ObjectMapper objectMapper;
    private final MonitoringSecurityProperties monitoringSecurityProperties;

    private Set<String> authorisedInstanceIds;

    @PostConstruct
    public void init() {
        this.authorisedInstanceIds = new HashSet<>(monitoringSecurityProperties.getAuthorisedInstanceIds());
        log.info("Initialized EC2 authentication filter with {} authorised instance IDs", authorisedInstanceIds.size());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(MONITORING_PATH);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        String clientIp = getClientIp(request);

        // Wrap the request to enable multiple reads of the request body
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

        try {
            MonitoringRequest monitoringRequest;

            try {
                monitoringRequest = parseMonitoringRequest(cachedRequest);
            } catch (Exception e) {
                log.warn("[MONITORING-AUTH-FAILED] Invalid JSON in request body | Path={} | IP={} | Error={}",
                        requestPath, clientIp, e.getMessage());
                sendUnauthorisedResponse(response, "Invalid request body format");
                return;
            }

            String pkcs7 = monitoringRequest.getPkcs7();

            log.debug("[MONITORING-AUTH] Received request | Pkcs7Length={} | Path={} | IP={}",
                    pkcs7 != null ? pkcs7.length() : 0, requestPath, clientIp);

            if (pkcs7 == null || pkcs7.isEmpty()) {
                log.warn("[MONITORING-AUTH-FAILED] Missing PKCS7 signature in request body | Path={} | IP={}",
                        requestPath, clientIp);
                sendUnauthorisedResponse(response, "PKCS7 signature required");
                return;
            }

            // Validate the PKCS7 signature and extract the instance identity
            // SECURITY: instanceId and document are extracted from PKCS7
            String requestTimestamp = monitoringRequest.getTimestamp();
            Ec2InstanceIdentityValidator.ValidationResult validationResult =
                instanceIdentityValidator.validatePkcs7(pkcs7, requestTimestamp);

            if (!validationResult.isValid()) {
                log.warn("[MONITORING-AUTH-FAILED] Instance identity validation failed | IP={} | Reason={}",
                        clientIp, validationResult.getErrorMessage());
                sendUnauthorisedResponse(response, "Invalid instance identity: " + validationResult.getErrorMessage());
                return;
            }

            // Get the validated instance ID from the PKCS7 signature
            String instanceId = validationResult.getInstanceId();

            if (!authorisedInstanceIds.contains(instanceId)) {
                log.warn("[MONITORING-AUTH-FAILED] Unauthorised instance ID | InstanceId={} | IP={}",
                        instanceId, clientIp);
                sendUnauthorisedResponse(response, "Instance not authorised");
                return;
            }

            log.info("[MONITORING-AUTH-SUCCESS] EC2 instance authenticated | InstanceId={} | IP={} | Path={}",
                    instanceId, clientIp, requestPath);

            cachedRequest.setAttribute("ec2_instance_id", instanceId);
            cachedRequest.setAttribute("ec2_authenticated", true);
            cachedRequest.setAttribute("monitoring_request", monitoringRequest);

        } catch (IOException e) {
            log.error("[MONITORING-AUTH-ERROR] IOException during authentication | Path={} | Error={}",
                    requestPath, e.getMessage());
            sendUnauthorisedResponse(response, "Authentication error");
            return;
        }

        filterChain.doFilter(cachedRequest, response);
    }

    /**
     * Parse request body into MonitoringRequest DTO.
     * Note: Uses CachedBodyHttpServletRequest which allows multiple reads of the body.
     *
     * @param request the HTTP request (should be a CachedBodyHttpServletRequest)
     * @return parsed MonitoringRequest object
     * @throws IOException if reading the request body fails
     */
    private MonitoringRequest parseMonitoringRequest(CachedBodyHttpServletRequest request) throws IOException {
        String body = request.getBodyAsString();
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
     * Send 401 Unauthorised response with ErrorResponse DTO.
     */
    private void sendUnauthorisedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                Collections.singletonList(message)
        );

        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
