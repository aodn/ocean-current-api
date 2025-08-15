package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.remoteJson.JsonPathsRoot;
import au.org.aodn.oceancurrent.configuration.remoteJson.RemoteServiceProperties;
import au.org.aodn.oceancurrent.exception.RemoteFileException;
import au.org.aodn.oceancurrent.model.RemoteJsonDataGroup;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RemoteJsonService {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final JsonPathsRoot jsonPathsConfig;
    private final RemoteServiceProperties remoteProperties;

    public List<String> getFullUrls() {
        validateBaseUrl();

        return jsonPathsConfig.getProducts().stream()
                .flatMap(product -> product.getPaths().stream())
                .map(this::buildFullUrl)
                .collect(Collectors.toList());
    }

    private void validateBaseUrl() {
        String baseUrl = "https://oceancurrent.edge.aodn.org.au/resource/";
        log.info("Validating base URL: {}", baseUrl);
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new RemoteFileException("Base URL is not configured");
        }
    }

    private String buildFullUrl(String path) {
        try {
            return UriComponentsBuilder
                    .fromUriString("https://oceancurrent.edge.aodn.org.au/resource/")
                    .path(path)
                    .build()
                    .toString();
        } catch (IllegalArgumentException e) {
            throw new RemoteFileException("Invalid base URL or path configuration", e);
        }
    }

    public List<RemoteJsonDataGroup> fetchJsonFromUrl(String url) {
        try {
            log.info("Fetching JSON data for URL with path: {}", url);

            String jsonResponse = restTemplate.getForObject(url, String.class);

            if (jsonResponse == null) {
                throw new RemoteFileException("Received null response from remote server");
            }

            List<RemoteJsonDataGroup> metadata = objectMapper.readValue(
                    jsonResponse,
                    new TypeReference<>() {}
            );

            log.debug("Successfully fetched and parsed JSON data");
            return metadata;

        } catch (RestClientException e) {
            log.error("Failed to fetch JSON data: {}", e.getMessage());
            throw new RemoteFileException("Failed to fetch remote JSON file", e);
        } catch (IOException e) {
            log.error("Failed to parse JSON data: {}", e.getMessage());
            throw new RemoteFileException("Failed to parse remote JSON file", e);
        }
    }
}
