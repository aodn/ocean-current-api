package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.remoteJson.RemoteServiceProperties;
import au.org.aodn.oceancurrent.constant.CacheNames;
import au.org.aodn.oceancurrent.dto.RegionLatestDate;
import au.org.aodn.oceancurrent.dto.RegionLatestDateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RemoteLatestDateService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_DAYS_BACK = 30;

    private final RestTemplate restTemplate;
    private final CacheManager cacheManager;
    private final Map<String, String> productBaseUrls = new ConcurrentHashMap<>();

    private final RemoteServiceProperties remoteProperties;

    public RemoteLatestDateService(RestTemplate restTemplate, CacheManager cacheManager, RemoteServiceProperties remoteProperties) {
        this.restTemplate = restTemplate;
        this.cacheManager = cacheManager;
        this.remoteProperties = remoteProperties;
        initializeProductUrls();
    }

    private void initializeProductUrls() {
        productBaseUrls.put("argo", remoteProperties.getResourceBaseUrl() + "profiles/map/");
    }

    @Cacheable(value = CacheNames.ARGO_LATEST_DATE, key = "#productId")
    public RegionLatestDateResponse getLatestDateByProductId(String productId) {
        String latestDate = fetchLatestDateForProduct(productId);

        RegionLatestDate regionLatestDate = new RegionLatestDate("", latestDate, "");
        return new RegionLatestDateResponse(productId, List.of(regionLatestDate));
    }

    @Scheduled(fixedRate = 4 * 60 * 60 * 1000) // Every 4 hours
    @CacheEvict(value = CacheNames.ARGO_LATEST_DATE, allEntries = true)
    public void updateAllLatestDates() {
        log.info("Starting scheduled remote latest date polling - evicting and refreshing cache");

        // Prefetch fresh data for all products after cache eviction
        for (String productId : productBaseUrls.keySet()) {
            try {
                String latestDate = fetchLatestDateForProduct(productId);
                if (latestDate != null) {
                    RegionLatestDate regionLatestDate = new RegionLatestDate("", latestDate, "");
                    RegionLatestDateResponse response = new RegionLatestDateResponse(productId, List.of(regionLatestDate));

                    // Manually populate the cache
                    Objects.requireNonNull(cacheManager.getCache(CacheNames.ARGO_LATEST_DATE)).put(productId, response);
                    log.info("Refreshed cache for product: {} with date: {}", productId, latestDate);
                } else {
                    log.warn("No latest date found for product: {}", productId);
                }
            } catch (Exception e) {
                log.error("Failed to refresh cache for product: {}", productId, e);
            }
        }
    }

    private String fetchLatestDateForProduct(String productId) {
        String baseUrl = productBaseUrls.get(productId);
        if (baseUrl == null) {
            log.warn("No base URL configured for product: {}", productId);
            return null;
        }

        LocalDate currentDate = LocalDate.now();

        for (int daysBack = 0; daysBack <= MAX_DAYS_BACK; daysBack++) {
            LocalDate checkDate = currentDate.minusDays(daysBack);
            String dateStr = checkDate.format(DATE_FORMATTER);
            String url = baseUrl + dateStr + ".gif";

            try {
                log.debug("Checking {} date: {} at URL: {}", productId, dateStr, url);
                restTemplate.headForHeaders(url);
                log.info("Found latest {} date: {}", productId, dateStr);
                return dateStr;
            } catch (Exception e) {
                log.debug("{} file not found for date: {}", productId, dateStr);
            }
        }

        log.warn("No {} files found within {} days", productId, MAX_DAYS_BACK);
        return null;
    }
}
