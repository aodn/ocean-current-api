package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.configuration.GlidersCatalogueProperties;
import au.org.aodn.oceancurrent.configuration.remoteJson.RemoteServiceProperties;
import au.org.aodn.oceancurrent.exception.GlobalExceptionHandler;
import au.org.aodn.oceancurrent.service.GlidersCatalogueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlidersMissionControllerTest {
    private MockMvc mvc;
    private RestTemplate remote;
    private static final String URL = "https://example.org/resource/gliders/gliders-catalogue.json";

    @BeforeEach
    void setUp() throws Exception {
        remote = mock(RestTemplate.class);
        var properties = new RemoteServiceProperties();
        properties.setBaseUrl("https://example.org");
        var service = new GlidersCatalogueService(remote, new ObjectMapper(), properties, new GlidersCatalogueProperties());
        mvc = MockMvcBuilders.standaloneSetup(new GlidersMissionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var fixture = new ClassPathResource("gliders/catalogue-v1.json")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        when(remote.getForEntity(URL, String.class)).thenReturn(ResponseEntity.ok(fixture));
    }

    @Test
    void defaultsAndResponseContract() throws Exception {
        mvc.perform(get("/gliders/missions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.order").value("deployment_date"))
                .andExpect(jsonPath("$.missions[0].id").value("TwoRocks20260327@5.00"))
                .andExpect(jsonPath("$.missions[0].processing_version").value("5.00"))
                .andExpect(jsonPath("$.missions[0].is_nrt").value(false));
    }

    @Test
    void filtersOrdersAndPages() throws Exception {
        mvc.perform(get("/gliders/missions").param("order", "mission_name")
                        .param("q", "TWOrocks").param("nrt", "FALSE").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.missions[0].id").value("TwoRocks20260327@5.00"));
        mvc.perform(get("/gliders/missions").param("q", "no-match"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.missions").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TwoRocks20260327@5.00", "TwoRocks20260327@5.", "Brisbane20180306A_nrt@3.0."})
    void findsExactIdsIncludingAtAndTrailingPunctuation(String id) throws Exception {
        mvc.perform(get("/gliders/missions/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void preservesQuotedProcessingVersionInUrlSegment() throws Exception {
        String fixture = new ClassPathResource("gliders/catalogue-v1.json")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("TwoRocks20260327@5.00", "TwoRocks20260327@5.0%2Btest")
                .replace("\"5.00\"", "\"5.0+test\"");
        when(remote.getForEntity(URL, String.class)).thenReturn(ResponseEntity.ok(fixture));
        mvc.perform(get("/gliders/missions/{id}", "TwoRocks20260327@5.0%2Btest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("TwoRocks20260327@5.0%2Btest"))
                .andExpect(jsonPath("$.processing_version").value("5.0+test"));
    }

    @Test
    void returns404ForUnknownMission() throws Exception {
        mvc.perform(get("/gliders/missions/missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Not Found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"order=wrong", "page=-1", "page=abc", "page=2147483648", "size=0", "size=101", "size=no", "nrt=yes", "nrt="})
    void rejectsInvalidParameters(String query) throws Exception {
        mvc.perform(get("/gliders/missions?" + query)).andExpect(status().isBadRequest());
        verifyNoInteractions(remote);
    }

    @Test
    void returns503WhenNoValidCatalogueIsAvailable() throws Exception {
        when(remote.getForEntity(URL, String.class)).thenThrow(new ResourceAccessException("timeout"));
        mvc.perform(get("/gliders/missions")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/gliders/missions/unknown")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void returns503ForMalformedCatalogue() throws Exception {
        when(remote.getForEntity(URL, String.class)).thenReturn(ResponseEntity.ok("not json"));
        mvc.perform(get("/gliders/missions")).andExpect(status().isServiceUnavailable());
    }
}
