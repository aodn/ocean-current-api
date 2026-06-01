package au.org.aodn.oceancurrent.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lets a trailing slash resolve to the same handler, e.g.
 * {@code /metadata/latest-dates/sixDaySst-sst/} behaves like
 * {@code /metadata/latest-dates/sixDaySst-sst}.
 *
 * <p>Spring Boot 3 dropped trailing-slash matching by default, but upstream proxies
 * (e.g. AWS Amplify) can append one. We strip it by wrapping the request and
 * continuing the same chain — no redirect (which could loop if the proxy re-adds
 * the slash) and no deprecated path-match config. Running first means routing and
 * security both see the trimmed path. Swagger, API-docs and actuator paths are left alone.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrailingSlashNormalizationFilter extends OncePerRequestFilter {

    // Framework-managed paths whose trailing-slash handling we must not interfere with:
    // springdoc UI/docs/webjars and the actuator base-path (management.endpoints.web.base-path).
    private static final List<String> EXCLUDED_PREFIXES =
            List.of("/swagger-ui", "/v3/api-docs", "/webjars", "/manage");

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.length() <= 1 || !uri.endsWith("/")) {
            return true;
        }
        String withinContext = uri.substring(request.getContextPath().length());
        return EXCLUDED_PREFIXES.stream().anyMatch(withinContext::startsWith);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String trimmedUri = request.getRequestURI().substring(0, request.getRequestURI().length() - 1);
        filterChain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getRequestURI() {
                return trimmedUri;
            }
        }, response);
    }
}
