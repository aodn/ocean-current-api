package au.org.aodn.oceancurrent.configuration;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrailingSlashNormalizationFilterTest {

    private final TrailingSlashNormalizationFilter filter = new TrailingSlashNormalizationFilter();

    @Test
    void trailingSlash_isTrimmedFromWrappedRequest() throws Exception {
        HttpServletRequest forwarded = filterAndCapture("/metadata/latest-dates/sixDaySst-sst/");

        assertEquals("/metadata/latest-dates/sixDaySst-sst", forwarded.getRequestURI());
    }

    @Test
    void pathWithoutTrailingSlash_isLeftUnchanged() throws Exception {
        HttpServletRequest forwarded = filterAndCapture("/metadata/latest-dates/sixDaySst-sst");

        assertEquals("/metadata/latest-dates/sixDaySst-sst", forwarded.getRequestURI());
    }

    @Test
    void swaggerUiPath_isNotTrimmed() throws Exception {
        HttpServletRequest forwarded = filterAndCapture("/swagger-ui/");

        assertEquals("/swagger-ui/", forwarded.getRequestURI());
    }

    @Test
    void apiDocsPath_isNotTrimmed() throws Exception {
        HttpServletRequest forwarded = filterAndCapture("/v3/api-docs/");

        assertEquals("/v3/api-docs/", forwarded.getRequestURI());
    }

    @Test
    void trailingSlash_onImageListPath_isTrimmed() throws Exception {
        HttpServletRequest forwarded = filterAndCapture("/metadata/image-list/sixDaySst-sst/");

        assertEquals("/metadata/image-list/sixDaySst-sst", forwarded.getRequestURI());
    }

    @Test
    void trailingSlash_withQueryString_trimsPathAndKeepsQuery() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/metadata/image-list/sixDaySst-sst/");
        request.setQueryString("region=NW");
        request.setParameter("region", "NW");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest forwarded = (HttpServletRequest) Objects.requireNonNull(chain.getRequest());
        assertEquals("/metadata/image-list/sixDaySst-sst", forwarded.getRequestURI());
        assertEquals("region=NW", forwarded.getQueryString());
        assertEquals("NW", forwarded.getParameter("region"));
    }

    @Test
    void actuatorPath_isNotTrimmed() throws Exception {
        HttpServletRequest forwarded = filterAndCapture("/manage/health/");

        assertEquals("/manage/health/", forwarded.getRequestURI());
    }

    @Test
    void rootPath_isLeftUnchanged() throws Exception {
        HttpServletRequest forwarded = filterAndCapture("/");

        assertEquals("/", forwarded.getRequestURI());
    }

    @Test
    void contextRoot_withContextPath_isLeftUnchanged() throws Exception {
        // With context-path /api/v1, a request to the context root (/api/v1/) must not be
        // trimmed to /api/v1, since within the context that is the root path "/".
        HttpServletRequest forwarded = filterAndCaptureWithContext("/api/v1", "/api/v1/");

        assertEquals("/api/v1/", forwarded.getRequestURI());
    }

    @Test
    void trailingSlash_withContextPath_isTrimmed() throws Exception {
        HttpServletRequest forwarded =
                filterAndCaptureWithContext("/api/v1", "/api/v1/metadata/image-list/sixDaySst-sst/");

        assertEquals("/api/v1/metadata/image-list/sixDaySst-sst", forwarded.getRequestURI());
    }

    private HttpServletRequest filterAndCapture(String requestUri) throws Exception {
        return filterAndCaptureWithContext("", requestUri);
    }

    private HttpServletRequest filterAndCaptureWithContext(String contextPath, String requestUri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        request.setContextPath(contextPath);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        return (HttpServletRequest) Objects.requireNonNull(chain.getRequest());
    }
}
