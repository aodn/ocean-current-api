package au.org.aodn.oceancurrent.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "production"})
public class Ec2InstanceAuthenticationFilterProductionProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testMonitoringEndpoint_WithoutPkcs7_ReturnUnauthorised() throws Exception {
        String requestBody = "{\"errorMessage\": \"Test error\"}";

        mockMvc.perform(post("/api/v1/monitoring/fatal-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.errors[0]").value("PKCS7 signature required"));
    }
}
