package au.org.aodn.oceancurrent.security;

import org.junit.jupiter.api.BeforeEach;
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
 * Tests that monitoring endpoints require EC2 instance identity document + PKCS7 signature.
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

    private String mockInstanceId;
    private String mockDocument;
    private String mockPkcs7;

    @BeforeEach
    public void setUp() {
        // Mock EC2 instance identity data for testing
        mockInstanceId = "i-0123456789abcdef0";
        mockDocument = "{\"instanceId\":\"i-0123456789abcdef0\",\"region\":\"ap-southeast-2\",\"accountId\":\"123456789012\"}";
        mockPkcs7 = "MIAGCSqGSIb3DQEHAqCAMIACAQExCzAJBgUrDgMCGgUAMIAGCSqGSIb3DQEHAaCAJIAEggHceyJp==";
    }

    @Test
    public void testMonitoringEndpoint_WithoutInstanceId_ReturnUnauthorised() throws Exception {
        // Given: A request to monitoring endpoint without instance ID
        String requestBody = "{\"errorMessage\": \"Test error\"}";

        // When & Then: Request should be rejected
        mockMvc.perform(post("/api/v1/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Unauthorised"))
                .andExpect(jsonPath("$.message").value("Instance ID required in request body"));
    }

    @Test
    public void testMonitoringEndpoint_WithInstanceIdButNoDocument_ReturnUnauthorised() throws Exception {
        // Given: A request with instance ID but missing document
        String requestBody = String.format("{\"instanceId\": \"%s\", \"errorMessage\": \"Test error\"}", mockInstanceId);

        // When & Then: Request should be rejected
        mockMvc.perform(post("/api/v1/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Unauthorised"))
                .andExpect(jsonPath("$.message").value("Instance identity document required"));
    }

    @Test
    public void testMonitoringEndpoint_WithInstanceIdAndDocumentButNoPkcs7_ReturnUnauthorised() throws Exception {
        // Given: A request with instance ID and document but missing PKCS7 signature
        String requestBody = String.format(
            "{\"instanceId\": \"%s\", \"document\": \"%s\", \"errorMessage\": \"Test error\"}",
            mockInstanceId,
            mockDocument.replace("\"", "\\\"")
        );

        // When & Then: Request should be rejected
        mockMvc.perform(post("/api/v1/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("PKCS7 signature required"));
    }

    @Test
    public void testMonitoringEndpoint_WithAllFieldsButInvalidSignature_ReturnUnauthorised() throws Exception {
        // Given: A request with all required fields but invalid PKCS7 signature
        String requestBody = String.format(
            "{\"instanceId\": \"%s\", \"document\": \"%s\", \"pkcs7\": \"%s\", \"errorMessage\": \"Test error\"}",
            mockInstanceId,
            mockDocument.replace("\"", "\\\""),
            "invalid-signature"
        );

        // When & Then: Request should be rejected due to invalid signature
        mockMvc.perform(post("/api/v1/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorised"))
                .andExpect(jsonPath("$.message").exists());
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
    public void testMonitoringEndpoint_WithUnauthorisedInstanceId_ReturnUnauthorised() throws Exception {
        // Given: A request from an instance ID not in the whitelist
        String unauthorisedInstanceId = "i-9999999999999999";
        String unauthorisedDocument = "{\"instanceId\":\"i-9999999999999999\",\"region\":\"ap-southeast-2\"}";

        String requestBody = String.format(
            "{\"instanceId\": \"%s\", \"document\": \"%s\", \"pkcs7\": \"%s\", \"errorMessage\": \"Test error\"}",
            unauthorisedInstanceId,
            unauthorisedDocument.replace("\"", "\\\""),
            mockPkcs7
        );

        // When & Then: Request should be rejected - not in whitelist
        mockMvc.perform(post("/api/v1/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorised"));
    }

    @Test
    public void testMonitoringEndpoint_GetRequest_RequiresAuth() throws Exception {
        // Given: A GET request to monitoring endpoint without authentication

        // When & Then: Request should be rejected
        mockMvc.perform(get("/api/v1/monitoring/fatal-log"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorised"));
    }
}
