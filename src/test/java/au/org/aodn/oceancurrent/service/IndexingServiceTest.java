package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.elasticsearch.ElasticsearchProperties;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexingServiceTest {

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private RemoteJsonService remoteJsonService;

    @Mock
    private S3Service s3Service;

    @Mock
    private ElasticsearchProperties esProperties;

    @Mock
    private CacheManager cacheManager;

    private IndexingService indexingService;

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd't'HHmmssSSS");

    @BeforeEach
    void setUp() {
        when(esProperties.getIndexName()).thenReturn("ocean-current-files");
        indexingService = new IndexingService(esClient, remoteJsonService, s3Service, esProperties, cacheManager);
    }

    @Test
    void testGenerateTimestampedIndexName_generatesCorrectFormat() throws Exception {
        // Given
        String baseIndexName = "ocean-current-files";

        // When
        String result = invokeGenerateTimestampedIndexName(baseIndexName);

        // Then
        // Verify format: ocean-current-files-yyyyMMddtHHmmssSSS
        assertThat(result).startsWith(baseIndexName + "-");
        assertThat(result).hasSize(baseIndexName.length() + 1 + 18); // base + "-" + timestamp (18 chars)

        // Extract timestamp part and verify it's valid
        String timestampPart = result.substring(baseIndexName.length() + 1);
        assertThat(timestampPart).matches("\\d{8}t\\d{9}");
    }

    @Test
    void testGenerateTimestampedIndexName_timestampIsCurrentTime() throws Exception {
        // Given
        String baseIndexName = "ocean-current-files";
        LocalDateTime beforeCall = LocalDateTime.now();

        // When
        String result = invokeGenerateTimestampedIndexName(baseIndexName);

        // Then
        LocalDateTime afterCall = LocalDateTime.now();
        String timestampPart = result.substring(baseIndexName.length() + 1);
        LocalDateTime generatedTime = LocalDateTime.parse(timestampPart, TIMESTAMP_FORMAT);

        // Generated time should be between before and after
        assertThat(generatedTime).isAfterOrEqualTo(beforeCall.minusSeconds(1));
        assertThat(generatedTime).isBeforeOrEqualTo(afterCall.plusSeconds(1));
    }

    @Test
    void testGenerateTimestampedIndexName_differentBaseIndexName() throws Exception {
        // Given
        String baseIndexName = "test-index";

        // When
        String result = invokeGenerateTimestampedIndexName(baseIndexName);

        // Then
        assertThat(result).startsWith(baseIndexName + "-");
        assertThat(result).matches("test-index-\\d{8}t\\d{9}");
    }

    @Test
    void testGenerateTimestampedIndexName_uniqueTimestamps() throws Exception {
        // Given
        String baseIndexName = "ocean-current-files";

        // When - generate two timestamps in quick succession
        String result1 = invokeGenerateTimestampedIndexName(baseIndexName);
        Thread.sleep(1000); // Wait 1 second to ensure different timestamp
        String result2 = invokeGenerateTimestampedIndexName(baseIndexName);

        // Then - they should be different
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    void testGenerateTimestampedIndexName_exampleFormat() throws Exception {
        // Given
        String baseIndexName = "ocean-current-files";

        // When
        String result = invokeGenerateTimestampedIndexName(baseIndexName);

        // Then - verify it matches expected pattern like "ocean-current-files-20251203t142530123"
        assertThat(result).matches("ocean-current-files-\\d{8}t\\d{9}");
    }

    @Test
    void testGenerateTimestampedIndexName_isLowercase() throws Exception {
        // Given
        String baseIndexName = "ocean-current-files";

        // When
        String result = invokeGenerateTimestampedIndexName(baseIndexName);

        // Then - verify entire index name is lowercase (Elasticsearch requirement)
        assertThat(result).isEqualTo(result.toLowerCase());
        assertThat(result).doesNotContain("T"); // uppercase T not allowed
        assertThat(result).contains("t"); // lowercase t separator
    }

    // Helper method for reflection-based testing of private method

    private String invokeGenerateTimestampedIndexName(String baseIndexName) throws Exception {
        Method method = IndexingService.class.getDeclaredMethod("generateTimestampedIndexName", String.class);
        method.setAccessible(true);
        return (String) method.invoke(indexingService, baseIndexName);
    }
}
