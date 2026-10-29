package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.GlidersCatalogueProperties;
import au.org.aodn.oceancurrent.configuration.remoteJson.RemoteServiceProperties;
import au.org.aodn.oceancurrent.exception.GlidersCatalogueUnavailableException;
import au.org.aodn.oceancurrent.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlidersCatalogueServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate remote = mock(RestTemplate.class);
    private final AtomicLong time = new AtomicLong();
    private GlidersCatalogueService service;
    private String json;
    private static final String URL = "https://example.org/resource/api/gliders-catalogue.json";
    private static final String ID = "TwoRocks20260327@5.00";

    @BeforeEach
    void setUp() throws IOException {
        json = new ClassPathResource("gliders/catalogue-v1.json").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var properties = new GlidersCatalogueProperties();
        properties.setRefreshInterval(Duration.ofSeconds(10));
        var config = new RemoteServiceProperties();
        config.setBaseUrl("https://example.org");
        service = new GlidersCatalogueService(remote, mapper, config, properties, time::get, Runnable::run);
        respond(json);
    }

    private void respond(String body) {
        doReturn(ResponseEntity.ok(body)).when(remote).getForEntity(URL, String.class);
    }

    @Test
    void loadsAndPreservesMissionIdentityAndOptionalMetadata() {
        var mission = service.mission(ID);
        assertThat(mission.processingVersion()).isEqualTo("5.00");
        assertThat(service.mission("TwoRocks20260327@5.").processingVersion()).isEqualTo("5.");
        assertThat(service.mission("Brisbane20180306A_nrt@3.0.").processingVersion()).isEqualTo("3.0.");
        assertThat(service.mission("Perth20110626_1@unknown").processingVersion()).isNull();
        assertThat(mission.visualisations().details()).hasSize(1);
        assertThat(mission.latitude()).isEqualTo(-31.5);
        assertThat(mission.startDate()).isEqualTo("2026-03-27");
        assertThatThrownBy(() -> service.mission("unknown")).isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deployment_date", "mission_name", "distance_alongshore_to_darwin", "event_based_sampling"})
    void usesExactSourceOrderAndMembership(String order) throws Exception {
        var result = service.missions(order, null, null, 0, 100);
        var expected = mapper.readTree(json).path("orders").path(order);
        assertThat(result.missions().stream().map(m -> m.id()).toList())
                .containsExactlyElementsOf(mapper.convertValue(expected, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {}));
        assertThat(result.total()).isEqualTo(expected.size());
        assertThat(result.order()).isEqualTo(order);
    }

    @Test
    void acceptsEmptyEventMembershipAndUnlabelledSourceLinks() throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree(json);
        root.withObject("orders").withArray("event_based_sampling").removeAll();
        for (var mission : root.path("missions")) {
            if (mission.path("id").asText().equals(ID)) {
                ((ObjectNode) mission.path("visualisations").path("details").get(0)).put("label", "");
            }
        }
        respond(root.toString());
        assertThat(service.missions("event_based_sampling", null, null, 0, 25).total()).isZero();
        assertThat(service.mission(ID).visualisations().details().get(0).label()).isEmpty();
    }

    @Test
    void filtersBeforePaginationAndHandlesEmptyAndDistantPages() {
        var page = service.missions("deployment_date", "sLoCuM", false, 1, 1);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.missions()).extracting(m -> m.id()).containsExactly("TwoRocks20260327@5.");
        assertThat(service.missions("deployment_date", "BRISBANE", true, 0, 25).total()).isEqualTo(1);
        assertThat(service.missions("deployment_date", null, false, 0, 25).total()).isEqualTo(3);
        assertThat(service.missions("event_based_sampling", "Brisbane", null, 0, 25).missions()).isEmpty();
        assertThat(service.missions("deployment_date", "absent", null, 0, 25).total()).isZero();
        assertThat(service.missions("deployment_date", null, null, Integer.MAX_VALUE, 100).missions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "overflow-version", "missing-missions", "missing-orders", "bad-order", "unknown-reference", "duplicate-reference", "missing-id", "duplicate-id", "unstable-id", "numeric-version", "bad-visualisations", "missing-nrt", "bad-latest", "bad-image"})
    void rejectsInvalidSchema(String problem) throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree(json);
        ObjectNode mission = (ObjectNode) root.path("missions").get(0);
        switch (problem) {
            case "version" -> root.put("schema_version", 2);
            case "overflow-version" -> root.put("schema_version", 4294967297L);
            case "missing-missions" -> root.remove("missions");
            case "missing-orders" -> root.remove("orders");
            case "bad-order" -> ((ObjectNode) root.path("orders")).put("mission_name", "wrong");
            case "unknown-reference" -> root.withObject("orders").withArray("mission_name").add("unknown");
            case "duplicate-reference" -> root.withObject("orders").withArray("deployment_date").add(ID);
            case "missing-id" -> mission.remove("id");
            case "duplicate-id" -> root.withArray("missions").add(mission.deepCopy());
            case "unstable-id" -> mission.put("id", "row-1");
            case "numeric-version" -> mission.put("processing_version", 3.0);
            case "bad-visualisations" -> mission.remove("visualisations");
            case "missing-nrt" -> mission.remove("is_nrt");
            case "bad-latest" -> ((ObjectNode) mission.path("visualisations")).put("latest_images", "wrong");
            case "bad-image" -> ((ObjectNode) mission.path("visualisations"))
                    .putObject("latest_images").put("4d", 123);
        }
        respond(root.toString());
        assertThatThrownBy(() -> service.mission(ID)).isInstanceOf(GlidersCatalogueUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[]", "", "{} {}"})
    void rejectsMalformedOrInvalidJson(String body) {
        respond(body);
        assertThatThrownBy(() -> service.mission(ID)).isInstanceOf(GlidersCatalogueUnavailableException.class);
    }

    @Test
    void handles404TimeoutAndEmptyResponseAndCanRecover() {
        when(remote.getForEntity(URL, String.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND))
                .thenThrow(new ResourceAccessException("timeout", new java.net.SocketTimeoutException()))
                .thenReturn(ResponseEntity.noContent().build())
                .thenReturn(ResponseEntity.ok(json));
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.mission(ID)).isInstanceOf(GlidersCatalogueUnavailableException.class);
        }
        assertThat(service.mission(ID).id()).isEqualTo(ID);
    }

    @Test
    void cachesAndRefreshesWithoutReplacingValidDataOnFailure() {
        var original = service.mission(ID);
        service.missions("mission_name", null, null, 0, 25);
        verify(remote, times(1)).getForEntity(URL, String.class);
        respond("invalid json");
        time.addAndGet(Duration.ofSeconds(11).toNanos());
        assertThat(service.mission(ID)).isEqualTo(original);
        when(remote.getForEntity(URL, String.class)).thenThrow(new ResourceAccessException("timeout"));
        assertThat(service.mission(ID)).isEqualTo(original);
        respond(json.replace("Slocum-1", "Updated"));
        service.mission(ID); // refresh request may receive the previous snapshot
        assertThat(service.mission(ID).gliderId()).isEqualTo("Updated");
        verify(remote, times(4)).getForEntity(URL, String.class);
    }

    @Test
    void rejectsInvalidQueriesBeforeLoading() {
        assertThatThrownBy(() -> service.missions("invalid", null, null, 0, 25)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.missions("mission_name", null, null, -1, 25)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.missions("mission_name", null, null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.missions("mission_name", null, null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(remote);
    }
}
