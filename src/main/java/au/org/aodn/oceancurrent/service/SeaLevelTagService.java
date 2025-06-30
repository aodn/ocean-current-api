package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.dto.GenericTagResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example implementation for sea level tag service that gets data from a REST API.
 * This demonstrates how to integrate with external REST APIs as data sources.
 */
@Slf4j
@Service
public class SeaLevelTagService implements ProductTagService {

    private static final String PRODUCT_TYPE = "sea-level";

    private final RestTemplate restTemplate;

    @Value("${ocean-current.sea-level.api.base-url:https://api.example.com/sea-level}")
    private String baseApiUrl;

    @Value("${ocean-current.sea-level.api.key:}")
    private String apiKey;

    public SeaLevelTagService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getProductType() {
        return PRODUCT_TYPE;
    }

    @Override
    public boolean downloadData() {
        log.info("Sea level data is fetched on-demand from REST API - no download needed");
        // For REST APIs, we typically don't "download" data but fetch it on-demand
        return isDataAvailable();
    }

    @Override
    public boolean isDataAvailable() {
        try {
            log.debug("Checking sea level API availability");

            // Try to hit the API health endpoint
            String healthUrl = baseApiUrl + "/health";

            // Add API key if configured
            if (!apiKey.isEmpty()) {
                healthUrl += "?api_key=" + apiKey;
            }

            restTemplate.getForObject(healthUrl, String.class);
            log.debug("Sea level API is available");
            return true;

        } catch (Exception e) {
            log.debug("Sea level API not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean hasData() {
        try {
            // Check if API has any data by querying for available dates
            List<String> tagFiles = getAllTagFiles();
            return !tagFiles.isEmpty();
        } catch (Exception e) {
            log.debug("Error checking if sea level API has data: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> getAllTagFiles() {
        try {
            log.debug("Getting all sea level tag files from REST API");

            String url = baseApiUrl + "/dates";
            if (!apiKey.isEmpty()) {
                url += "?api_key=" + apiKey;
            }

            // Call API to get available dates/files
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("dates")) {
                @SuppressWarnings("unchecked")
                List<String> dates = (List<String>) response.get("dates");

                // Convert dates to tag file format
                return dates.stream()
                        .map(date -> constructTagFilePath(date))
                        .toList();
            }

            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Error getting sea level tag files from API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get sea level tag files from API", e);
        }
    }

    @Override
    public boolean tagFileExists(String tagFile) {
        try {
            log.debug("Checking if sea level tag file {} exists via API", tagFile);

            // Extract date from tag file path
            String dateTime = extractDateFromTagFile(tagFile);

            String url = baseApiUrl + "/data/" + dateTime + "/exists";
            if (!apiKey.isEmpty()) {
                url += "?api_key=" + apiKey;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            return response != null && Boolean.TRUE.equals(response.get("exists"));

        } catch (Exception e) {
            log.error("Error checking if sea level tag file exists: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Object getTagsByTagFile(String tagFile) {
        try {
            log.debug("Getting sea level tags for tag file {} from REST API", tagFile);

            // Extract date from tag file path
            String dateTime = extractDateFromTagFile(tagFile);

            String url = baseApiUrl + "/data/" + dateTime;
            if (!apiKey.isEmpty()) {
                url += "?api_key=" + apiKey;
            }

            // Call API to get tag data for specific date
            @SuppressWarnings("unchecked")
            Map<String, Object> apiResponse = restTemplate.getForObject(url, Map.class);

            if (apiResponse == null || !apiResponse.containsKey("tags")) {
                throw new RuntimeException("No tag data found in API response");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tags = (List<Map<String, Object>>) apiResponse.get("tags");

            return new GenericTagResponse(PRODUCT_TYPE, tagFile, tags);

        } catch (Exception e) {
            log.error("Error getting sea level tags from API for tag file {}: {}", tagFile, e.getMessage(), e);
            throw new RuntimeException("Failed to get sea level tags from API: " + tagFile, e);
        }
    }

    @Override
    public String constructTagFilePath(String dateTime) {
        if (!isValidDateFormat(dateTime)) {
            throw new IllegalArgumentException("Sea level date must be in format YYYYMMDD (8 digits)");
        }

        // Sea level tag file format: sealevel/YYYY/MM/YYYYMMDD_sealevel.json
        String year = dateTime.substring(0, 4);
        String month = dateTime.substring(4, 6);

        return String.format("sealevel/%s/%s/%s_sealevel.json", year, month, dateTime);
    }

    @Override
    public boolean isValidDateFormat(String dateTime) {
        // Sea level uses YYYYMMDD format
        return dateTime != null && dateTime.length() == 8 && dateTime.matches("\\d{8}");
    }

    /**
     * Extract date from tag file path
     * Example: "sealevel/2023/01/20230115_sealevel.json" -> "20230115"
     */
    private String extractDateFromTagFile(String tagFile) {
        String fileName = tagFile.substring(tagFile.lastIndexOf('/') + 1);
        return fileName.split("_")[0]; // Get part before underscore
    }
}
