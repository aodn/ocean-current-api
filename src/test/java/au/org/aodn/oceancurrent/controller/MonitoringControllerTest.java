package au.org.aodn.oceancurrent.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit test for MonitoringController.
 * Tests the controller logic without authentication filters.
 * See Ec2InstanceAuthenticationFilterIntegrationTest for authentication tests.
 */
@WebMvcTest(MonitoringController.class)
@AutoConfigureMockMvc(addFilters = false)  // Disable security filters for testing
public class MonitoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testTriggerFatalLog_NoRequestBody() throws Exception {
        // When & Then - request body is optional, should use default message
        mockMvc.perform(post("/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.message", is("Fatal log generated successfully for monitoring purposes")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.logLevel", is("FATAL")))
                .andExpect(jsonPath("$.loggedError", is("Monitoring health check endpoint triggered")));
    }

    @Test
    public void testTriggerFatalLog_WithErrorMessage() throws Exception {
        // Given
        String requestBody = """
                {
                    "errorMessage": "Test error from unit test"
                }
                """;

        // When & Then
        mockMvc.perform(post("/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.message", is("Fatal log generated successfully for monitoring purposes")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.logLevel", is("FATAL")))
                .andExpect(jsonPath("$.loggedError", is("Test error from unit test")));
    }

    @Test
    public void testTriggerFatalLog_WithErrorMessageAndSource() throws Exception {
        // Given
        String requestBody = """
                {
                    "errorMessage": "File scan failed while generating JSON index",
                    "source": "daily-sync-job"
                }
                """;

        // When & Then
        mockMvc.perform(post("/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.logLevel", is("FATAL")))
                .andExpect(jsonPath("$.loggedError", is("File scan failed while generating JSON index [source=daily-sync-job]")));
    }

    @Test
    public void testTriggerFatalLog_WithAllFields() throws Exception {
        // Given
        String requestBody = """
                {
                    "errorMessage": "Cron job failed: file scan timeout while generating JSON index",
                    "source": "daily-data-sync",
                    "context": "exit_code=1, retry_count=3"
                }
                """;

        // When & Then
        mockMvc.perform(post("/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.message", is("Fatal log generated successfully for monitoring purposes")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.logLevel", is("FATAL")))
                .andExpect(jsonPath("$.loggedError",
                        is("Cron job failed: file scan timeout while generating JSON index [source=daily-data-sync] [context=exit_code=1, retry_count=3]")));
    }

    @Test
    public void testTriggerFatalLog_WithEmptyErrorMessage() throws Exception {
        // Given - empty error message should use default
        String requestBody = """
                {
                    "errorMessage": "",
                    "source": "test-job"
                }
                """;

        // When & Then
        mockMvc.perform(post("/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.loggedError", is("Monitoring health check endpoint triggered [source=test-job]")));
    }

    @Test
    public void testTriggerFatalLog_WithOnlyContext() throws Exception {
        // Given
        String requestBody = """
                {
                    "context": "manual_test=true"
                }
                """;

        // When & Then
        mockMvc.perform(post("/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.loggedError",
                        is("Monitoring health check endpoint triggered [context=manual_test=true]")));
    }

    @Test
    public void testTriggerFatalLog_WithEmptyRequestBody() throws Exception {
        // Given
        String requestBody = "{}";

        // When & Then
        mockMvc.perform(post("/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.loggedError", is("Monitoring health check endpoint triggered")));
    }
}
