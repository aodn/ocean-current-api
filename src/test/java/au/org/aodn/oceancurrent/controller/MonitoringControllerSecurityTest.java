package au.org.aodn.oceancurrent.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security test class for MonitoringController.
 * Tests API key authentication for monitoring endpoints.
 * Uses @SpringBootTest to load full application context with security filters enabled.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonitoringControllerSecurityTest {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Autowired
    private MockMvc mockMvc;

    @Value("${monitoring.api-key}")
    private String testApiKey;

    @Test
    void testPing_WithValidApiKey_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/monitoring/ping")
                .header(API_KEY_HEADER, testApiKey)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.message").value("API is reachable"));
    }

    @Test
    void testPing_WithoutApiKey_Unauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/monitoring/ping")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"));
    }

    @Test
    void testPing_WithInvalidApiKey_Unauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/monitoring/ping")
                .header(API_KEY_HEADER, "invalid-api-key")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"));
    }

    @Test
    void testPing_WithEmptyApiKey_Unauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/monitoring/ping")
                .header(API_KEY_HEADER, "")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"));
    }

    @Test
    void testPing_WithApiKeyHavingWhitespace_Success() throws Exception {
        // Given - API key with leading/trailing whitespace
        String apiKeyWithSpaces = "  " + testApiKey + "  ";

        // When & Then - Should succeed as the filter trims the API key
        mockMvc.perform(get("/monitoring/ping")
                .header(API_KEY_HEADER, apiKeyWithSpaces)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void testReportFailure_WithValidApiKey_Success() throws Exception {
        // Given
        String requestJson = "{\"message\":\"Test alert\",\"source\":\"integration-test\"}";

        // When & Then
        mockMvc.perform(post("/monitoring/alert")
                .header(API_KEY_HEADER, testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("logged"))
                .andExpect(jsonPath("$.message").value("Failure logged successfully"));
    }

    @Test
    void testReportFailure_WithoutApiKey_Unauthorized() throws Exception {
        // Given
        String requestJson = "{\"message\":\"Test alert\",\"source\":\"integration-test\"}";

        // When & Then
        mockMvc.perform(post("/monitoring/alert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"));
    }

    @Test
    void testReportFailure_WithInvalidApiKey_Unauthorized() throws Exception {
        // Given
        String requestJson = "{\"message\":\"Test alert\",\"source\":\"integration-test\"}";

        // When & Then
        mockMvc.perform(post("/monitoring/alert")
                .header(API_KEY_HEADER, "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"));
    }

    @Test
    void testNonMonitoringEndpoint_NoAuthRequired() throws Exception {
        // When & Then - Other endpoints (non-monitoring paths) should not require API key
        // Test that a non-monitoring endpoint doesn't trigger authentication
        // Using the home endpoint which exists and doesn't require authentication
        mockMvc.perform(get("/")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // Should return 200 OK without API key
        // The important part is that it's not 401 Unauthorized - proving the auth filter
        // only applies to /monitoring endpoints
    }
}
