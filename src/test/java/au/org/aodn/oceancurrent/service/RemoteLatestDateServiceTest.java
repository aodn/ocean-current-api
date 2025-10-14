package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.remoteJson.RemoteServiceProperties;
import au.org.aodn.oceancurrent.dto.RegionLatestDate;
import au.org.aodn.oceancurrent.dto.RegionLatestDateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RemoteLatestDateServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private RemoteServiceProperties remoteProperties;

    private RemoteLatestDateService remoteLatestDateService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String TODAY = LocalDate.now().format(DATE_FORMATTER);
    private static final String YESTERDAY = LocalDate.now().minusDays(1).format(DATE_FORMATTER);

    @BeforeEach
    void setUp() {
        when(remoteProperties.getBaseUrl()).thenReturn("https://oceancurrent.edge.aodn.org.au/resource/");
        remoteLatestDateService = new RemoteLatestDateService(restTemplate, cacheManager, remoteProperties);
    }

    @Test
    void getLatestDateByProductId_ValidArgoProduct_ReturnsLatestDate() {
        // Given - today's file exists
        String expectedUrl = "https://oceancurrent.edge.aodn.org.au/resource/profiles/map/" + TODAY + ".gif";
        when(restTemplate.headForHeaders(expectedUrl)).thenReturn(new HttpHeaders());

        // When
        RegionLatestDateResponse response = remoteLatestDateService.getLatestDateByProductId("argo");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getProductId()).isEqualTo("argo");
        assertThat(response.getRegionLatestDates()).hasSize(1);

        RegionLatestDate latestDate = response.getRegionLatestDates().get(0);
        assertThat(latestDate.getRegion()).isEmpty();
        assertThat(latestDate.getLatestDate()).isEqualTo(TODAY);
        assertThat(latestDate.getPath()).isEmpty();

        verify(restTemplate, times(1)).headForHeaders(expectedUrl);
    }

    @Test
    void getLatestDateByProductId_TodayNotAvailable_ReturnsYesterday() {
        // Given - today's file doesn't exist, but yesterday's does
        String todayUrl = "https://oceancurrent.edge.aodn.org.au/resource/profiles/map/" + TODAY + ".gif";
        String yesterdayUrl = "https://oceancurrent.edge.aodn.org.au/resource/profiles/map/" + YESTERDAY + ".gif";

        when(restTemplate.headForHeaders(todayUrl)).thenThrow(new RestClientException("404 Not Found"));
        when(restTemplate.headForHeaders(yesterdayUrl)).thenReturn(new HttpHeaders());

        // When
        RegionLatestDateResponse response = remoteLatestDateService.getLatestDateByProductId("argo");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getProductId()).isEqualTo("argo");
        assertThat(response.getRegionLatestDates()).hasSize(1);

        RegionLatestDate latestDate = response.getRegionLatestDates().get(0);
        assertThat(latestDate.getLatestDate()).isEqualTo(YESTERDAY);

        verify(restTemplate, times(1)).headForHeaders(todayUrl);
        verify(restTemplate, times(1)).headForHeaders(yesterdayUrl);
    }

    @Test
    void getLatestDateByProductId_NoFilesFound_ReturnsNull() {
        // Given - all file checks fail
        when(restTemplate.headForHeaders(anyString())).thenThrow(new RestClientException("404 Not Found"));

        // When
        RegionLatestDateResponse response = remoteLatestDateService.getLatestDateByProductId("argo");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getProductId()).isEqualTo("argo");
        assertThat(response.getRegionLatestDates()).hasSize(1);

        RegionLatestDate latestDate = response.getRegionLatestDates().get(0);
        assertThat(latestDate.getLatestDate()).isNull();

        // Should check up to 31 times (today + 30 days back)
        verify(restTemplate, times(31)).headForHeaders(anyString());
    }

    @Test
    void getLatestDateByProductId_InvalidProduct_ReturnsNull() {
        // When
        RegionLatestDateResponse response = remoteLatestDateService.getLatestDateByProductId("invalid-product");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getProductId()).isEqualTo("invalid-product");
        assertThat(response.getRegionLatestDates()).hasSize(1);

        RegionLatestDate latestDate = response.getRegionLatestDates().get(0);
        assertThat(latestDate.getLatestDate()).isNull();

        // Should not make any HTTP calls for invalid products
        verify(restTemplate, times(0)).headForHeaders(anyString());
    }

}
