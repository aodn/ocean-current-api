package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.GlidersCatalogueProperties;
import au.org.aodn.oceancurrent.configuration.remoteJson.RemoteServiceProperties;
import au.org.aodn.oceancurrent.dto.GliderMissionPage;
import au.org.aodn.oceancurrent.exception.GlidersCatalogueUnavailableException;
import au.org.aodn.oceancurrent.exception.ResourceNotFoundException;
import au.org.aodn.oceancurrent.model.gliders.GliderMission;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

@Service
@Slf4j
public class GlidersCatalogueService {
    private static final List<String> ORDERS = List.of("deployment_date", "mission_name",
            "distance_alongshore_to_darwin", "event_based_sampling");
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String catalogueUrl;
    // Kept outside the product CacheManager so its daily eviction cannot discard stale valid data.
    private final LoadingCache<String, Catalogue> cache;

    @Autowired
    public GlidersCatalogueService(RestTemplate restTemplate, ObjectMapper objectMapper,
                                   RemoteServiceProperties remote, GlidersCatalogueProperties properties) {
        this(restTemplate, objectMapper, remote, properties, Ticker.systemTicker(), ForkJoinPool.commonPool());
    }

    GlidersCatalogueService(RestTemplate restTemplate, ObjectMapper objectMapper,
                            RemoteServiceProperties remote, GlidersCatalogueProperties properties,
                            Ticker ticker, Executor executor) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.catalogueUrl = remote.getResourceBaseUrl().replaceAll("/+$", "") + "/api/gliders-catalogue.json";
        this.cache = Caffeine.newBuilder().maximumSize(1).ticker(ticker).executor(executor)
                .refreshAfterWrite(properties.getRefreshInterval()).build(key -> loadCatalogue());
    }

    public GliderMissionPage missions(String order, String q, Boolean nrt, int page, int size) {
        if (!ORDERS.contains(order)) {
            throw new IllegalArgumentException("Invalid order. Supported orders: " + String.join(", ", ORDERS));
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        }
        Catalogue catalogue = cache.get("catalogue");
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<GliderMission> matching = catalogue.orders().get(order).stream()
                .map(catalogue.missions()::get)
                .filter(m -> nrt == null || m.isNrt() == nrt)
                .filter(m -> m.missionName().toLowerCase(Locale.ROOT).contains(query)
                        || (m.gliderId() != null && m.gliderId().toLowerCase(Locale.ROOT).contains(query)))
                .toList();
        int from = (int) Math.min((long) page * size, matching.size());
        int to = Math.min(from + size, matching.size());
        return new GliderMissionPage(matching.subList(from, to), matching.size(), page, size, order);
    }

    public GliderMission mission(String id) {
        GliderMission mission = cache.get("catalogue").missions().get(id);
        if (mission == null) {
            throw new ResourceNotFoundException("Gliders mission", id);
        }
        return mission;
    }

    private Catalogue loadCatalogue() {
        try {
            var response = restTemplate.getForEntity(catalogueUrl, String.class);
            require(response.getStatusCode().is2xxSuccessful() && response.getBody() != null,
                    "No successful catalogue response");
            JsonNode root = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(response.getBody());
            require(root != null && root.isObject(), "Expected catalogue object");
            require(root.path("schema_version").isIntegralNumber()
                    && root.path("schema_version").canConvertToInt()
                    && root.path("schema_version").intValue() == 1, "Unsupported schema_version");
            require(text(root.path("source_url")), "Missing source_url");
            require(root.path("missions").isArray() && !root.path("missions").isEmpty(), "Missing missions");
            require(root.path("orders").isObject(), "Missing orders");
            Map<String, GliderMission> missions = new LinkedHashMap<>();
            for (JsonNode node : root.path("missions")) {
                validateMission(node);
                GliderMission mission = objectMapper.treeToValue(node, GliderMission.class);
                String version = mission.processingVersion() == null ? "unknown" : mission.processingVersion();
                String expectedId = UriUtils.encode(mission.missionName(), StandardCharsets.UTF_8) + "@"
                        + UriUtils.encode(version, StandardCharsets.UTF_8);
                require(expectedId.equals(mission.id()), "Invalid stable mission ID");
                require(missions.putIfAbsent(mission.id(), mission) == null, "Duplicate mission ID");
            }
            Map<String, List<String>> orders = new LinkedHashMap<>();
            for (String order : ORDERS) {
                JsonNode values = root.path("orders").path(order);
                require(values.isArray(), "Missing order: " + order);
                var seen = new HashSet<String>();
                for (JsonNode id : values) {
                    require(text(id) && missions.containsKey(id.textValue()) && seen.add(id.textValue()),
                            "Invalid or duplicate order reference: " + order);
                }
                require(order.equals("event_based_sampling") || !values.isEmpty(), "Empty order: " + order);
                orders.put(order, objectMapper.convertValue(values,
                        new TypeReference<List<String>>() {}));
            }
            return new Catalogue(Map.copyOf(missions), Map.copyOf(orders));
        } catch (Exception ex) {
            log.warn("Unable to load Gliders mission catalogue: {}", ex.getMessage());
            throw new GlidersCatalogueUnavailableException(ex);
        }
    }

    private void validateMission(JsonNode node) {
        require(node.isObject(), "Expected mission object");
        for (String field : List.of("id", "mission_id", "mission_name")) {
            require(text(node.path(field)), "Missing mission " + field);
        }
        require(node.path("is_nrt").isBoolean(), "Missing is_nrt");
        require(node.path("catalogue_numbers").isArray(), "Missing catalogue_numbers");
        node.path("catalogue_numbers").forEach(n -> require(text(n), "Invalid catalogue number"));
        for (String field : List.of("processing_version", "dates_raw", "start_date", "end_date",
                "glider_id", "temperature_salinity_url", "scientific_data_url")) {
            require(node.has(field) && (node.get(field).isNull() || node.get(field).isTextual()),
                    "Invalid mission " + field);
        }
        for (String field : List.of("latitude", "longitude", "duration_days", "distance_travelled_km")) {
            require(node.has(field) && (node.get(field).isNull() || node.get(field).isNumber()),
                    "Invalid mission " + field);
        }
        for (String kind : List.of("details", "animations")) {
            JsonNode links = node.path("visualisations").path(kind);
            require(links.isArray(), "Missing visualisations " + kind);
            links.forEach(link -> require(link.path("label").isTextual() && text(link.path("url")),
                    "Invalid visualisation link"));
        }
    }

    private static boolean text(JsonNode node) {
        return node.isTextual() && !node.textValue().isBlank();
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }

    private record Catalogue(Map<String, GliderMission> missions, Map<String, List<String>> orders) {}
}
