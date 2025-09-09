package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.constant.CacheNames;
import au.org.aodn.oceancurrent.dto.RegionLatestDate;
import au.org.aodn.oceancurrent.dto.RegionLatestDateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RemoteLatestDateService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_DAYS_BACK = 30;

    private final RestTemplate restTemplate;
    private final Map<String, String> cachedLatestDates = new ConcurrentHashMap<>();
    private final Map<String, String> productBaseUrls = new ConcurrentHashMap<>();

    public RemoteLatestDateService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        initializeProductUrls();
    }

    private void initializeProductUrls() {
        productBaseUrls.put("argo", "https://oceancurrent.edge.aodn.org.au/resource/profiles/map/");
    }

    @Cacheable(value = CacheNames.ARGO_LATEST_DATE, key = "#productId")
    public RegionLatestDateResponse getLatestDateByProductId(String productId) {
        String latestDate = cachedLatestDates.get(productId);
        if (latestDate == null) {
            latestDate = fetchLatestDateForProduct(productId);
            if (latestDate != null) {
                cachedLatestDates.put(productId, latestDate);
            }
        }

        RegionLatestDate regionLatestDate = new RegionLatestDate("", latestDate, "");
        return new RegionLatestDateResponse(productId, List.of(regionLatestDate));
    }

    @Scheduled(fixedRate = 4 * 60 * 60 * 1000) // Every 4 hours
    public void updateAllLatestDates() {
        log.info("Starting scheduled remote latest date polling");

        for (String productId : productBaseUrls.keySet()) {
            String newLatestDate = fetchLatestDateForProduct(productId);
            String currentCachedDate = cachedLatestDates.get(productId);

            if (newLatestDate != null && !newLatestDate.equals(currentCachedDate)) {
                cachedLatestDates.put(productId, newLatestDate);
                log.info("Updated {} latest date to: {}", productId, newLatestDate);
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
