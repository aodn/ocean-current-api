package au.org.aodn.oceancurrent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authentication filter for internal monitoring endpoints.
 * Only active in production and edge environments.
 * Validates AWS IAM authentication using SigV4 signatures.
 */
@Component
@Profile({"prod", "edge"})
@Slf4j
public class AwsIamAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String AWS_DATE_HEADER = "X-Amz-Date";
    private static final String AWS_SECURITY_TOKEN_HEADER = "X-Amz-Security-Token";
    private static final String MONITORING_PATH = "/api/v1/monitoring";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Only apply authentication to monitoring endpoints
        if (requestPath.startsWith(MONITORING_PATH)) {
            String authHeader = request.getHeader(AUTH_HEADER);
            String awsDate = request.getHeader(AWS_DATE_HEADER);

            // Check if request has AWS SigV4 signature
            if (authHeader == null || !authHeader.startsWith("AWS4-HMAC-SHA256")) {
                log.warn("Unauthorized access attempt to {} from IP: {} - Missing or invalid Authorization header",
                        requestPath, getClientIp(request));
                sendUnauthorizedResponse(response, "AWS IAM authentication required");
                return;
            }

            // Verify required AWS headers are present
            if (awsDate == null || awsDate.isEmpty()) {
                log.warn("Unauthorized access attempt to {} from IP: {} - Missing X-Amz-Date header",
                        requestPath, getClientIp(request));
                sendUnauthorizedResponse(response, "Invalid AWS signature - missing required headers");
                return;
            }

            log.info("AWS IAM authenticated request to {} from IP: {}", requestPath, getClientIp(request));
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"error\": \"Unauthorized\", \"message\": \"%s\"}", message));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
