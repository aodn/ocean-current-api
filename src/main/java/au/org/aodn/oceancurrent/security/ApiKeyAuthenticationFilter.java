package au.org.aodn.oceancurrent.security;

import au.org.aodn.oceancurrent.configuration.MonitoringProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import org.springframework.lang.NonNull;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String MONITORING_PATH = "/monitoring";

    private final MonitoringProperties monitoringProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Only apply authentication to monitoring endpoints
        if (requestPath.contains(MONITORING_PATH)) {
            String apiKey = extractApiKey(request);
            String clientIp = getClientIp(request);

            if (!isValidApiKey(apiKey)) {
                log.warn("Unauthorized access attempt to monitoring endpoint from IP: {}", clientIp);
                sendUnauthorizedResponse(response);
                return;
            }

            log.debug("Valid API key provided for monitoring endpoint access");
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValidApiKey(String apiKey) {
        String configuredApiKey = monitoringProperties.getApiKey();

        if (!StringUtils.hasText(configuredApiKey)) {
            log.error("Monitoring API key is not configured!");
            return false;
        }

        return StringUtils.hasText(apiKey) && apiKey.equals(configuredApiKey);
    }

    private String extractApiKey(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (StringUtils.hasText(apiKey)) {
            return apiKey.trim();
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void sendUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Invalid or missing API key\"}");
        response.getWriter().flush();
    }
}
