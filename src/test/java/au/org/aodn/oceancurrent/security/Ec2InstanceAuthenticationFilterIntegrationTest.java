package au.org.aodn.oceancurrent.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for EC2 Instance Identity authentication filter.
 * Tests that monitoring endpoints require a valid PKCS7 signature containing EC2 instance identity.
 * The instance identity and instance ID are extracted and validated from the PKCS7 signature.
 * <p>
 * Note: This test runs with the 'edge' profile to activate the authentication filter.
 * The 'test' profile provides the base configuration (Elasticsearch, etc).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "edge"})
public class Ec2InstanceAuthenticationFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testMonitoringEndpoint_WithoutPkcs7_ReturnUnauthorised() throws Exception {
        // Given: A request to monitoring endpoint without PKCS7 signature
        String requestBody = "{\"errorMessage\": \"Test error\"}";

        // When & Then: Request should be rejected
        mockMvc.perform(post("/api/v1/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.errors[0]").value("PKCS7 signature required"));
    }

    @Test
    public void testMonitoringEndpoint_WithEmptyPkcs7_ReturnUnauthorised() throws Exception {
        // Given: A request with empty PKCS7 signature
        String requestBody = "{\"pkcs7\": \"\", \"errorMessage\": \"Test error\"}";

        // When & Then: Request should be rejected
        mockMvc.perform(post("/api/v1/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.errors[0]").value("PKCS7 signature required"));
    }

    @Test
    public void testMonitoringEndpoint_WithInvalidPkcs7Signature_ReturnUnauthorised() throws Exception {
        // Given: A request with invalid PKCS7 signature
        String requestBody = "{\"pkcs7\": \"invalid-signature\", \"errorMessage\": \"Test error\"}";

        // When & Then: Request should be rejected due to invalid signature
        mockMvc.perform(post("/api/v1/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.errors[0]").value(containsString("Invalid instance identity")));
    }

    @Test
    public void testNonMonitoringEndpoint_WithoutAuth_DoesNotBlockRequest() throws Exception {
        // Given: A request to non-monitoring endpoint without EC2 instance authentication
        // When: Request is made without instance identity
        // Then: Request should NOT be blocked by authentication filter (may fail for other reasons but not auth)

        mockMvc.perform(get("/api/v1/manage/health")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().is(not(401)));  // Should NOT be 401 (Unauthorised)
    }

    @Test
    public void testMonitoringEndpoint_WithInvalidJsonBody_ReturnUnauthorised() throws Exception {
        // Given: A request with invalid JSON body
        String requestBody = "{invalid json}";

        // When & Then: Request should be rejected due to invalid JSON
        mockMvc.perform(post("/api/v1/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.errors[0]").value("Invalid request body format"));
    }

    @Test
    public void testMonitoringEndpoint_GetRequest_RequiresAuth() throws Exception {
        // Given: A GET request to monitoring endpoint without authentication
        // GET requests typically don't have a request body, so parsing will fail

        // When & Then: Request should be rejected with invalid request body format
        mockMvc.perform(get("/api/v1/monitoring/fatal-log"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.errors[0]").value("Invalid request body format"));
    }
}
