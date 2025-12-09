package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.configuration.MonitoringProperties;
import au.org.aodn.oceancurrent.security.ApiKeyAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for MonitoringController functionality (with security filters disabled).
 * For security/authentication tests, see MonitoringControllerSecurityTest.
 */
@WebMvcTest(MonitoringController.class)
@AutoConfigureMockMvc(addFilters = false)
class MonitoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MonitoringProperties monitoringProperties;

    @MockBean
    private ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Test
    void testPing_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/monitoring/ping")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").value("API is reachable"));
    }

    @Test
    void testReportFailure_WithMessageAndSource() throws Exception {
        // Given
        String requestJson = "{\"message\":\"Database connection failed\",\"source\":\"cron-job-01\"}";

        // When & Then
        mockMvc.perform(post("/monitoring/alert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("logged"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").value("Failure logged successfully"));
    }

    @Test
    void testReportFailure_WithMessageOnly() throws Exception {
        // Given
        String requestJson = "{\"message\":\"Unexpected error occurred\"}";

        // When & Then
        mockMvc.perform(post("/monitoring/alert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("logged"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").value("Failure logged successfully"));
    }

    @Test
    void testReportFailure_WithEmptyMessage() throws Exception {
        // Given
        String requestJson = "{\"message\":\"\",\"source\":\"test-source\"}";

        // When & Then
        mockMvc.perform(post("/monitoring/alert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("logged"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").value("Failure logged successfully"));
    }

    @Test
    void testReportFailure_WithNullMessage() throws Exception {
        // Given
        String requestJson = "{\"source\":\"test-source\"}";

        // When & Then
        mockMvc.perform(post("/monitoring/alert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("logged"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").value("Failure logged successfully"));
    }

    @Test
    void testReportFailure_WithSourceOnly() throws Exception {
        // Given
        String requestJson = "{\"source\":\"background-job\"}";

        // When & Then
        mockMvc.perform(post("/monitoring/alert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("logged"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").value("Failure logged successfully"));
    }

    @Test
    void testReportFailure_WithEmptyRequest() throws Exception {
        // Given
        String requestJson = "{}";

        // When & Then
        mockMvc.perform(post("/monitoring/alert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("logged"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").value("Failure logged successfully"));
    }
}
