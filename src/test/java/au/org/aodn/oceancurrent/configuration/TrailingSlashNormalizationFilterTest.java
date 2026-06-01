package au.org.aodn.oceancurrent.configuration;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
    void actuatorPath_isNotTrimmed() throws Exception {
        HttpServletRequest forwarded = filterAndCapture("/manage/health/");

        assertEquals("/manage/health/", forwarded.getRequestURI());
    }

    @Test
    void rootPath_isLeftUnchanged() throws Exception {
        HttpServletRequest forwarded = filterAndCapture("/");

        assertEquals("/", forwarded.getRequestURI());
    }

    private HttpServletRequest filterAndCapture(String requestUri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        return (HttpServletRequest) chain.getRequest();
    }
}
