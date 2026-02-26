package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.elasticsearch.ElasticsearchProperties;
import au.org.aodn.oceancurrent.exception.ReindexValidationException;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import au.org.aodn.oceancurrent.util.elasticsearch.IndexingCallback;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesResponse;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
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

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @Mock
    private Cache imageListCache;

    private IndexingService indexingService;

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd't'HHmmssSSS");
    private static final String INDEX_ALIAS = "ocean-current-files";

    @BeforeEach
    void setUp() {
        when(esProperties.getIndexName()).thenReturn(INDEX_ALIAS);
        lenient().when(esProperties.getMaxResultWindow()).thenReturn(10000);
        lenient().when(esProperties.getReindexValidationThresholdPercent()).thenReturn(80);
        lenient().when(esClient.indices()).thenReturn(indicesClient);
        lenient().when(cacheManager.getCache(any())).thenReturn(imageListCache);

        indexingService = new IndexingService(esClient, remoteJsonService, s3Service, esProperties, cacheManager);
    }

    @Nested
    class GenerateTimestampedIndexNameTests {
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
            Thread.sleep(2); // Wait 2ms to ensure different timestamp
            String result2 = invokeGenerateTimestampedIndexName(baseIndexName);

            // Then - they should be different
            assertThat(result1).isNotEqualTo(result2);
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
    }

    @Nested
    class ReindexAllHappyPathTests {
        @Test
        void testReindexAll_successfullyCreatesNewIndexAndSwitchesAlias() throws Exception {
            // Given
            doReturn(true).when(s3Service).isBucketAccessible();
            doReturn(Collections.emptyList()).when(s3Service).listAllSurfaceWaves();
            doReturn(Collections.emptyList()).when(remoteJsonService).getFullUrls();

            // Mock index creation
            doReturn(mock(CreateIndexResponse.class)).when(indicesClient).create(isA(CreateIndexRequest.class));

            // Mock alias doesn't exist initially
            doReturn(new BooleanResponse(false)).when(indicesClient).existsAlias(any(Function.class));

            // Mock refresh
            doReturn(mock(RefreshResponse.class)).when(indicesClient).refresh(any(Function.class));

            // Mock count for validation (new index has documents)
            CountResponse countResponse = mock(CountResponse.class);
            doReturn(100L).when(countResponse).count();
            doReturn(countResponse).when(esClient).count(any(Function.class));

            // Mock search for distinct productIds
            SearchResponse<ImageMetadataEntry> searchResp = createMockSearchResponseWithProductIds(Arrays.asList("product1", "product2"));
            doReturn(searchResp).when(esClient).search(any(Function.class), eq(ImageMetadataEntry.class));

            // Mock getIndicesForAlias (no old indices)
            lenient().doReturn(mock(GetAliasResponse.class)).when(indicesClient).getAlias(any(Function.class));

            // Mock alias update
            doReturn(mock(UpdateAliasesResponse.class)).when(indicesClient).updateAliases(any(Function.class));

            // Mock index exists check for cleanup
            lenient().doReturn(new BooleanResponse(true)).when(indicesClient).exists(any(Function.class));

            // When
            indexingService.reindexAll(true);

            // Then
            verify(indicesClient).create(isA(CreateIndexRequest.class)); // Index created
            verify(indicesClient).refresh(any(Function.class)); // Index refreshed
            verify(indicesClient).updateAliases(any(Function.class)); // Alias switched
            verify(imageListCache).clear(); // Cache cleared
        }

        @Test
        void testReindexAll_withCallback_reportsProgress() throws Exception {
            // Given
            IndexingCallback callback = mock(IndexingCallback.class);

            doReturn(true).when(s3Service).isBucketAccessible();
            doReturn(Collections.emptyList()).when(s3Service).listAllSurfaceWaves();
            doReturn(Collections.emptyList()).when(remoteJsonService).getFullUrls();

            doReturn(mock(CreateIndexResponse.class)).when(indicesClient).create(isA(CreateIndexRequest.class));
            doReturn(new BooleanResponse(false)).when(indicesClient).existsAlias(any(Function.class));
            doReturn(mock(RefreshResponse.class)).when(indicesClient).refresh(any(Function.class));

            CountResponse countResponse = mock(CountResponse.class);
            doReturn(100L).when(countResponse).count();
            doReturn(countResponse).when(esClient).count(any(Function.class));

            SearchResponse<ImageMetadataEntry> searchResp = createMockSearchResponseWithProductIds(Arrays.asList("product1"));
            doReturn(searchResp).when(esClient).search(any(Function.class), eq(ImageMetadataEntry.class));

            doReturn(mock(UpdateAliasesResponse.class)).when(indicesClient).updateAliases(any(Function.class));

            // When
            indexingService.reindexAll(true, callback);

            // Then
            verify(callback, atLeastOnce()).onProgress(anyString());
            verify(callback).onComplete(anyString());
            verify(callback, never()).onError(anyString());
        }

        @Test
        void testReindexAll_deletesOldIndicesAfterAliasSwitch() throws Exception {
            // Given
            String oldIndex1 = "ocean-current-files-20240101t000000000";
            String oldIndex2 = "ocean-current-files-20240102t000000000";

            doReturn(true).when(s3Service).isBucketAccessible();
            doReturn(Collections.emptyList()).when(s3Service).listAllSurfaceWaves();
            doReturn(Collections.emptyList()).when(remoteJsonService).getFullUrls();

            doReturn(mock(CreateIndexResponse.class)).when(indicesClient).create(isA(CreateIndexRequest.class));

            // Mock alias exists with old indices
            doReturn(new BooleanResponse(true)).when(indicesClient).existsAlias(any(Function.class));

            GetAliasResponse getAliasResponse = mock(GetAliasResponse.class);
            Map<String, IndexAliases> aliasMap = new HashMap<>();
            aliasMap.put(oldIndex1, mock(IndexAliases.class));
            aliasMap.put(oldIndex2, mock(IndexAliases.class));
            doReturn(aliasMap).when(getAliasResponse).result();
            doReturn(getAliasResponse).when(indicesClient).getAlias(any(Function.class));

            doReturn(mock(RefreshResponse.class)).when(indicesClient).refresh(any(Function.class));

            // Mock validation
            CountResponse currentCount = mock(CountResponse.class);
            doReturn(100L).when(currentCount).count();
            CountResponse newCount = mock(CountResponse.class);
            doReturn(100L).when(newCount).count();
            doReturn(currentCount, newCount).when(esClient).count(any(Function.class));

            doReturn(createMockSearchResponseWithProductIds(Arrays.asList("product1", "product2")))
                    .when(esClient).search(any(Function.class), eq(ImageMetadataEntry.class));

            doReturn(mock(UpdateAliasesResponse.class)).when(indicesClient).updateAliases(any(Function.class));
            doReturn(mock(DeleteIndexResponse.class)).when(indicesClient).delete(any(Function.class));

            // When
            indexingService.reindexAll(true);

            // Then
            verify(indicesClient, times(2)).delete(any(Function.class));
        }
    }

    @Nested
    class ReindexAllValidationTests {
        @Test
        void testReindexAll_failsWhenConfirmIsFalse() {
            // When & Then
            assertThatThrownBy(() -> indexingService.reindexAll(false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("confirm");
        }

        @Test
        void testReindexAll_failsWhenNewIndexIsEmpty() throws Exception {
            // Given
            doReturn(true).when(s3Service).isBucketAccessible();
            doReturn(Collections.emptyList()).when(s3Service).listAllSurfaceWaves();
            doReturn(Collections.emptyList()).when(remoteJsonService).getFullUrls();

            doReturn(mock(CreateIndexResponse.class)).when(indicesClient).create(isA(CreateIndexRequest.class));
            doReturn(new BooleanResponse(false)).when(indicesClient).existsAlias(any(Function.class));
            doReturn(mock(RefreshResponse.class)).when(indicesClient).refresh(any(Function.class));

            // Mock empty index (0 documents)
            CountResponse countResponse = mock(CountResponse.class);
            doReturn(0L).when(countResponse).count();
            doReturn(countResponse).when(esClient).count(any(Function.class));

            // Mock index exists for cleanup
            doReturn(new BooleanResponse(true)).when(indicesClient).exists(any(Function.class));
            doReturn(mock(DeleteIndexResponse.class)).when(indicesClient).delete(any(Function.class));

            // When & Then
            assertThatThrownBy(() -> indexingService.reindexAll(true))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Full reindexing failed")
                    .hasCauseInstanceOf(ReindexValidationException.class);

            // Verify cleanup was attempted
            verify(indicesClient).delete(any(Function.class));
        }

        @Test
        void testReindexAll_failsWhenNewIndexBelowThreshold() throws Exception {
            // Given
            doReturn(true).when(s3Service).isBucketAccessible();
            doReturn(Collections.emptyList()).when(s3Service).listAllSurfaceWaves();
            doReturn(Collections.emptyList()).when(remoteJsonService).getFullUrls();

            doReturn(mock(CreateIndexResponse.class)).when(indicesClient).create(isA(CreateIndexRequest.class));

            // Mock alias exists
            doReturn(new BooleanResponse(true)).when(indicesClient).existsAlias(any(Function.class));

            GetAliasResponse getAliasResponse = mock(GetAliasResponse.class);
            Map<String, IndexAliases> aliasMap = new HashMap<>();
            aliasMap.put("old-index", mock(IndexAliases.class));
            doReturn(aliasMap).when(getAliasResponse).result();
            doReturn(getAliasResponse).when(indicesClient).getAlias(any(Function.class));

            doReturn(mock(RefreshResponse.class)).when(indicesClient).refresh(any(Function.class));

            // Mock counts: old index has 1000 docs, new index has only 500 docs (50% < 80% threshold)
            // Note: Service calls getDocumentCount(newIndex) FIRST, then getDocumentCount(currentIndex)
            CountResponse newCountResponse = mock(CountResponse.class);
            doReturn(500L).when(newCountResponse).count();

            CountResponse oldCountResponse = mock(CountResponse.class);
            doReturn(1000L).when(oldCountResponse).count();

            doReturn(newCountResponse, oldCountResponse).when(esClient).count(any(Function.class));

            // Mock index exists for cleanup
            doReturn(new BooleanResponse(true)).when(indicesClient).exists(any(Function.class));
            doReturn(mock(DeleteIndexResponse.class)).when(indicesClient).delete(any(Function.class));

            // When & Then
            assertThatThrownBy(() -> indexingService.reindexAll(true))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Full reindexing failed")
                    .hasCauseInstanceOf(ReindexValidationException.class);

            // Verify cleanup was attempted
            verify(indicesClient).delete(any(Function.class));
        }

        @Test
        void testReindexAll_failsWhenProductIdsMissing() throws Exception {
            // Given
            doReturn(true).when(s3Service).isBucketAccessible();
            doReturn(Collections.emptyList()).when(s3Service).listAllSurfaceWaves();
            doReturn(Collections.emptyList()).when(remoteJsonService).getFullUrls();

            doReturn(mock(CreateIndexResponse.class)).when(indicesClient).create(isA(CreateIndexRequest.class));

            // Mock alias exists
            doReturn(new BooleanResponse(true)).when(indicesClient).existsAlias(any(Function.class));

            GetAliasResponse getAliasResponse = mock(GetAliasResponse.class);
            Map<String, IndexAliases> aliasMap = new HashMap<>();
            aliasMap.put("old-index", mock(IndexAliases.class));
            doReturn(aliasMap).when(getAliasResponse).result();
            doReturn(getAliasResponse).when(indicesClient).getAlias(any(Function.class));

            doReturn(mock(RefreshResponse.class)).when(indicesClient).refresh(any(Function.class));

            // Mock counts: both have enough documents
            CountResponse countResponse = mock(CountResponse.class);
            doReturn(1000L).when(countResponse).count();
            doReturn(countResponse).when(esClient).count(any(Function.class));

            // Mock productIds: old index has product1, product2, product3
            // new index only has product1, product2 (missing product3)
            SearchResponse<ImageMetadataEntry> oldResp = createMockSearchResponseWithProductIds(Arrays.asList("product1", "product2", "product3"));
            SearchResponse<ImageMetadataEntry> newResp = createMockSearchResponseWithProductIds(Arrays.asList("product1", "product2"));
            doReturn(oldResp, newResp).when(esClient).search(any(Function.class), eq(ImageMetadataEntry.class));

            // Mock index exists for cleanup
            doReturn(new BooleanResponse(true)).when(indicesClient).exists(any(Function.class));
            doReturn(mock(DeleteIndexResponse.class)).when(indicesClient).delete(any(Function.class));

            // When & Then
            assertThatThrownBy(() -> indexingService.reindexAll(true))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Full reindexing failed")
                    .hasCauseInstanceOf(ReindexValidationException.class);

            // Verify cleanup was attempted
            verify(indicesClient).delete(any(Function.class));
        }

        @Test
        void testReindexAll_successWhenNewIndexExceedsThreshold() throws Exception {
            // Given
            doReturn(true).when(s3Service).isBucketAccessible();
            doReturn(Collections.emptyList()).when(s3Service).listAllSurfaceWaves();
            doReturn(Collections.emptyList()).when(remoteJsonService).getFullUrls();

            doReturn(mock(CreateIndexResponse.class)).when(indicesClient).create(isA(CreateIndexRequest.class));

            // Mock alias exists
            doReturn(new BooleanResponse(true)).when(indicesClient).existsAlias(any(Function.class));

            GetAliasResponse getAliasResponse = mock(GetAliasResponse.class);
            Map<String, IndexAliases> aliasMap = new HashMap<>();
            aliasMap.put("old-index", mock(IndexAliases.class));
            doReturn(aliasMap).when(getAliasResponse).result();
            doReturn(getAliasResponse).when(indicesClient).getAlias(any(Function.class));

            doReturn(mock(RefreshResponse.class)).when(indicesClient).refresh(any(Function.class));

            // Mock counts: old index has 1000 docs, new index has 900 docs (90% >= 80% threshold)
            CountResponse oldCountResponse = mock(CountResponse.class);
            doReturn(1000L).when(oldCountResponse).count();

            CountResponse newCountResponse = mock(CountResponse.class);
            doReturn(900L).when(newCountResponse).count();

            doReturn(oldCountResponse, newCountResponse).when(esClient).count(any(Function.class));

            // Mock productIds: same in both indices
            doReturn(createMockSearchResponseWithProductIds(Arrays.asList("product1", "product2")))
                    .when(esClient).search(any(Function.class), eq(ImageMetadataEntry.class));

            doReturn(mock(UpdateAliasesResponse.class)).when(indicesClient).updateAliases(any(Function.class));
            doReturn(mock(DeleteIndexResponse.class)).when(indicesClient).delete(any(Function.class));

            // When
            indexingService.reindexAll(true);

            // Then
            verify(indicesClient).updateAliases(any(Function.class));
        }

        @Test
        void testReindexAll_succeedsWhenProductIdsMissingButSkipCheckEnabled() throws Exception {
            // Given
            doReturn(true).when(s3Service).isBucketAccessible();
            doReturn(Collections.emptyList()).when(s3Service).listAllSurfaceWaves();
            doReturn(Collections.emptyList()).when(remoteJsonService).getFullUrls();

            doReturn(mock(CreateIndexResponse.class)).when(indicesClient).create(isA(CreateIndexRequest.class));

            // Mock alias exists
            doReturn(new BooleanResponse(true)).when(indicesClient).existsAlias(any(Function.class));

            GetAliasResponse getAliasResponse = mock(GetAliasResponse.class);
            Map<String, IndexAliases> aliasMap = new HashMap<>();
            aliasMap.put("old-index", mock(IndexAliases.class));
            doReturn(aliasMap).when(getAliasResponse).result();
            doReturn(getAliasResponse).when(indicesClient).getAlias(any(Function.class));

            doReturn(mock(RefreshResponse.class)).when(indicesClient).refresh(any(Function.class));

            // Mock counts: both have enough documents
            CountResponse countResponse = mock(CountResponse.class);
            doReturn(1000L).when(countResponse).count();
            doReturn(countResponse).when(esClient).count(any(Function.class));

            // Mock productIds: old index has product1, product2, product3
            // new index only has product1, product2 (missing product3)
            SearchResponse<ImageMetadataEntry> oldResp = createMockSearchResponseWithProductIds(Arrays.asList("product1", "product2", "product3"));
            SearchResponse<ImageMetadataEntry> newResp = createMockSearchResponseWithProductIds(Arrays.asList("product1", "product2"));
            doReturn(oldResp, newResp).when(esClient).search(any(Function.class), eq(ImageMetadataEntry.class));

            // Enable skip productId check
            doReturn(true).when(esProperties).isReindexValidationSkipProductIdCheck();

            doReturn(mock(UpdateAliasesResponse.class)).when(indicesClient).updateAliases(any(Function.class));
            doReturn(mock(DeleteIndexResponse.class)).when(indicesClient).delete(any(Function.class));

            // When
            indexingService.reindexAll(true);

            // Then - should succeed despite missing productIds
            verify(indicesClient).updateAliases(any(Function.class));
        }

        @Test
        void testReindexAll_succeedsWithCallbackWarningWhenSkipCheckEnabled() throws Exception {
            // Given
            IndexingCallback callback = mock(IndexingCallback.class);

            doReturn(true).when(s3Service).isBucketAccessible();
            doReturn(Collections.emptyList()).when(s3Service).listAllSurfaceWaves();
            doReturn(Collections.emptyList()).when(remoteJsonService).getFullUrls();

            doReturn(mock(CreateIndexResponse.class)).when(indicesClient).create(isA(CreateIndexRequest.class));

            // Mock alias exists
            doReturn(new BooleanResponse(true)).when(indicesClient).existsAlias(any(Function.class));

            GetAliasResponse getAliasResponse = mock(GetAliasResponse.class);
            Map<String, IndexAliases> aliasMap = new HashMap<>();
            aliasMap.put("old-index", mock(IndexAliases.class));
            doReturn(aliasMap).when(getAliasResponse).result();
            doReturn(getAliasResponse).when(indicesClient).getAlias(any(Function.class));

            doReturn(mock(RefreshResponse.class)).when(indicesClient).refresh(any(Function.class));

            // Mock counts: both have enough documents
            CountResponse countResponse = mock(CountResponse.class);
            doReturn(1000L).when(countResponse).count();
            doReturn(countResponse).when(esClient).count(any(Function.class));

            // Mock productIds: missing product3 in new index
            SearchResponse<ImageMetadataEntry> oldResp = createMockSearchResponseWithProductIds(Arrays.asList("product1", "product2", "product3"));
            SearchResponse<ImageMetadataEntry> newResp = createMockSearchResponseWithProductIds(Arrays.asList("product1", "product2"));
            doReturn(oldResp, newResp).when(esClient).search(any(Function.class), eq(ImageMetadataEntry.class));

            // Enable skip productId check
            doReturn(true).when(esProperties).isReindexValidationSkipProductIdCheck();

            doReturn(mock(UpdateAliasesResponse.class)).when(indicesClient).updateAliases(any(Function.class));
            doReturn(mock(DeleteIndexResponse.class)).when(indicesClient).delete(any(Function.class));

            // When
            indexingService.reindexAll(true, callback);

            // Then - should succeed and report warning via callback
            verify(indicesClient).updateAliases(any(Function.class));
            verify(callback).onProgress(contains("productId check skipped"));
            verify(callback).onComplete(anyString());
            verify(callback, never()).onError(anyString());
        }

        @Test
        void testReindexAll_successWhenNewIndexHasAdditionalProductIds() throws Exception {
            // Given
            doReturn(true).when(s3Service).isBucketAccessible();
            doReturn(Collections.emptyList()).when(s3Service).listAllSurfaceWaves();
            doReturn(Collections.emptyList()).when(remoteJsonService).getFullUrls();

            doReturn(mock(CreateIndexResponse.class)).when(indicesClient).create(isA(CreateIndexRequest.class));

            doReturn(new BooleanResponse(true)).when(indicesClient).existsAlias(any(Function.class));

            GetAliasResponse getAliasResponse = mock(GetAliasResponse.class);
            Map<String, IndexAliases> aliasMap = new HashMap<>();
            aliasMap.put("old-index", mock(IndexAliases.class));
            doReturn(aliasMap).when(getAliasResponse).result();
            doReturn(getAliasResponse).when(indicesClient).getAlias(any(Function.class));

            doReturn(mock(RefreshResponse.class)).when(indicesClient).refresh(any(Function.class));

            CountResponse countResponse = mock(CountResponse.class);
            doReturn(1000L).when(countResponse).count();
            doReturn(countResponse).when(esClient).count(any(Function.class));

            // Mock productIds: old has product1, product2; new has product1, product2, product3 (new one added)
            SearchResponse<ImageMetadataEntry> oldResp2 = createMockSearchResponseWithProductIds(Arrays.asList("product1", "product2"));
            SearchResponse<ImageMetadataEntry> newResp2 = createMockSearchResponseWithProductIds(Arrays.asList("product1", "product2", "product3"));
            doReturn(oldResp2, newResp2).when(esClient).search(any(Function.class), eq(ImageMetadataEntry.class));

            doReturn(mock(UpdateAliasesResponse.class)).when(indicesClient).updateAliases(any(Function.class));
            doReturn(mock(DeleteIndexResponse.class)).when(indicesClient).delete(any(Function.class));

            // When
            indexingService.reindexAll(true);

            // Then - should succeed (additional productIds are OK)
            verify(indicesClient).updateAliases(any(Function.class));
        }
    }

    @Nested
    class AliasSwitchingTests {
        @Test
        void testSwitchAlias_removesOldAliasAndAddsNew() throws Exception {
            // Given
            String oldIndex = "ocean-current-files-old";
            String newIndex = "ocean-current-files-new";

            GetAliasResponse getAliasResponse = mock(GetAliasResponse.class);
            Map<String, IndexAliases> aliasMap = new HashMap<>();
            aliasMap.put(oldIndex, mock(IndexAliases.class));
            doReturn(aliasMap).when(getAliasResponse).result();

            doReturn(new BooleanResponse(true)).when(indicesClient).existsAlias(any(Function.class));
            doReturn(getAliasResponse).when(indicesClient).getAlias(any(Function.class));
            doReturn(mock(UpdateAliasesResponse.class)).when(indicesClient).updateAliases(any(Function.class));

            // When
            invokeSwitchAlias(INDEX_ALIAS, newIndex);

            // Then
            verify(indicesClient).updateAliases(any(Function.class));
        }

        @Test
        void testSwitchAlias_handlesNoExistingAlias() throws Exception {
            // Given
            String newIndex = "ocean-current-files-new";

            doReturn(new BooleanResponse(false)).when(indicesClient).existsAlias(any(Function.class));
            doReturn(mock(UpdateAliasesResponse.class)).when(indicesClient).updateAliases(any(Function.class));

            // When
            invokeSwitchAlias(INDEX_ALIAS, newIndex);

            // Then
            verify(indicesClient).updateAliases(any(Function.class));
        }

        @Test
        void testSwitchAlias_handlesMultipleOldIndices() throws Exception {
            // Given
            String oldIndex1 = "ocean-current-files-old1";
            String oldIndex2 = "ocean-current-files-old2";
            String newIndex = "ocean-current-files-new";

            GetAliasResponse getAliasResponse = mock(GetAliasResponse.class);
            Map<String, IndexAliases> aliasMap = new HashMap<>();
            aliasMap.put(oldIndex1, mock(IndexAliases.class));
            aliasMap.put(oldIndex2, mock(IndexAliases.class));
            doReturn(aliasMap).when(getAliasResponse).result();

            doReturn(new BooleanResponse(true)).when(indicesClient).existsAlias(any(Function.class));
            doReturn(getAliasResponse).when(indicesClient).getAlias(any(Function.class));
            doReturn(mock(UpdateAliasesResponse.class)).when(indicesClient).updateAliases(any(Function.class));

            // When
            invokeSwitchAlias(INDEX_ALIAS, newIndex);

            // Then
            verify(indicesClient).updateAliases(any(Function.class));
        }
    }

    @Nested
    class CleanupOldIndicesTests {
        @Test
        void testDeleteOldIndices_deletesAllOldIndices() throws Exception {
            // Given
            Set<String> oldIndices = new HashSet<>(Arrays.asList("old-index-1", "old-index-2"));

            doReturn(mock(DeleteIndexResponse.class)).when(indicesClient).delete(any(Function.class));

            // When
            invokeDeleteOldIndices(oldIndices);

            // Then
            verify(indicesClient, times(2)).delete(any(Function.class));
        }

        @Test
        void testDeleteOldIndices_handlesEmptySet() throws Exception {
            // Given
            Set<String> oldIndices = Collections.emptySet();

            // When
            invokeDeleteOldIndices(oldIndices);

            // Then
            verify(indicesClient, never()).delete(any(Function.class));
        }

        @Test
        void testDeleteOldIndices_continuesOnError() throws Exception {
            // Given
            Set<String> oldIndices = new HashSet<>(Arrays.asList("old-index-1", "old-index-2"));

            doThrow(new IOException("Delete failed"))
                    .doReturn(mock(DeleteIndexResponse.class))
                    .when(indicesClient).delete(any(Function.class));

            // When
            invokeDeleteOldIndices(oldIndices);

            // Then - should still try to delete both despite first one failing
            verify(indicesClient, times(2)).delete(any(Function.class));
        }
    }

    @Nested
    class RollbackBehaviorTests {
        @Test
        void testReindexAll_cleansUpNewIndexOnFailure() throws Exception {
            // Given
            doReturn(true).when(s3Service).isBucketAccessible();
            doReturn(Collections.emptyList()).when(remoteJsonService).getFullUrls();

            doReturn(mock(CreateIndexResponse.class)).when(indicesClient).create(isA(CreateIndexRequest.class));

            // Mock alias check for getIndicesForAlias
            doReturn(new BooleanResponse(false)).when(indicesClient).existsAlias(any(Function.class));

            // Make S3 service throw exception during indexing
            doThrow(new RuntimeException("S3 error")).when(s3Service).listAllSurfaceWaves();

            // Mock index exists for cleanup
            doReturn(new BooleanResponse(true)).when(indicesClient).exists(any(Function.class));
            doReturn(mock(DeleteIndexResponse.class)).when(indicesClient).delete(any(Function.class));

            // When & Then
            assertThatThrownBy(() -> indexingService.reindexAll(true))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Full reindexing failed");

            // Verify cleanup was attempted
            verify(indicesClient).delete(any(Function.class));
        }

        @Test
        void testReindexAll_handlesCleanupFailureGracefully() throws Exception {
            // Given
            doReturn(true).when(s3Service).isBucketAccessible();
            doReturn(Collections.emptyList()).when(remoteJsonService).getFullUrls();

            doReturn(mock(CreateIndexResponse.class)).when(indicesClient).create(isA(CreateIndexRequest.class));

            // Mock alias check for getIndicesForAlias
            doReturn(new BooleanResponse(false)).when(indicesClient).existsAlias(any(Function.class));

            // Make S3 service throw exception during indexing
            doThrow(new RuntimeException("S3 error")).when(s3Service).listAllSurfaceWaves();

            // Mock index exists for cleanup
            doReturn(new BooleanResponse(true)).when(indicesClient).exists(any(Function.class));

            // Cleanup also fails
            doThrow(new IOException("Cleanup failed")).when(indicesClient).delete(any(Function.class));

            // When & Then - should wrap the original cause
            assertThatThrownBy(() -> indexingService.reindexAll(true))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Full reindexing failed")
                    .hasCauseInstanceOf(RuntimeException.class); // Original S3 error cause should be wrapped
        }

        @Test
        void testReindexAll_doesNotDeleteIndexIfNotCreated() throws Exception {
            // Given
            // All mocks are lenient because they may not be called depending on where the exception occurs
            lenient().doReturn(true).when(s3Service).isBucketAccessible();

            // Fail during index creation
            lenient().doThrow(new IOException("Create failed")).when(indicesClient).create(isA(CreateIndexRequest.class));

            // Mock index doesn't exist
            lenient().doReturn(new BooleanResponse(false)).when(indicesClient).exists(any(Function.class));

            // When & Then
            assertThatThrownBy(() -> indexingService.reindexAll(true))
                    .isInstanceOf(RuntimeException.class);

            // Verify delete was never called since index wasn't created
            verify(indicesClient, never()).delete(any(Function.class));
        }

        @Test
        void testReindexAll_callbackReceivesErrorOnFailure() throws Exception {
            // Given
            IndexingCallback callback = mock(IndexingCallback.class);

            doReturn(true).when(s3Service).isBucketAccessible();
            doReturn(Collections.emptyList()).when(remoteJsonService).getFullUrls();

            doReturn(mock(CreateIndexResponse.class)).when(indicesClient).create(isA(CreateIndexRequest.class));

            // Mock alias check for getIndicesForAlias
            doReturn(new BooleanResponse(false)).when(indicesClient).existsAlias(any(Function.class));

            // Make S3 service throw exception
            doThrow(new RuntimeException("S3 error")).when(s3Service).listAllSurfaceWaves();

            doReturn(new BooleanResponse(true)).when(indicesClient).exists(any(Function.class));
            doReturn(mock(DeleteIndexResponse.class)).when(indicesClient).delete(any(Function.class));

            // When
            try {
                indexingService.reindexAll(true, callback);
            } catch (Exception e) {
                // Expected
            }

            // Then
            verify(callback).onError(contains("Failed to complete full reindexing"));
            verify(callback, never()).onComplete(anyString());
        }
    }

    // Helper methods for reflection-based testing of private methods

    private String invokeGenerateTimestampedIndexName(String baseIndexName) throws Exception {
        Method method = IndexingService.class.getDeclaredMethod("generateTimestampedIndexName", String.class);
        method.setAccessible(true);
        return (String) method.invoke(indexingService, baseIndexName);
    }

    private void invokeSwitchAlias(String aliasName, String newIndexName) throws Exception {
        Method method = IndexingService.class.getDeclaredMethod("switchAlias", String.class, String.class);
        method.setAccessible(true);
        method.invoke(indexingService, aliasName, newIndexName);
    }

    private void invokeDeleteOldIndices(Set<String> oldIndices) throws Exception {
        Method method = IndexingService.class.getDeclaredMethod("deleteOldIndices", Set.class);
        method.setAccessible(true);
        method.invoke(indexingService, oldIndices);
    }

    private SearchResponse<ImageMetadataEntry> createMockSearchResponseWithProductIds(List<String> productIds) {
        // Create all mocks first
        SearchResponse<ImageMetadataEntry> searchResponse = mock(SearchResponse.class);
        StringTermsAggregate termsAggregate = mock(StringTermsAggregate.class);
        co.elastic.clients.elasticsearch._types.aggregations.Buckets<StringTermsBucket> bucketContainer =
                mock(co.elastic.clients.elasticsearch._types.aggregations.Buckets.class);
        co.elastic.clients.elasticsearch._types.aggregations.Aggregate aggregate =
                mock(co.elastic.clients.elasticsearch._types.aggregations.Aggregate.class);
        HitsMetadata<ImageMetadataEntry> hits = mock(HitsMetadata.class);

        // Create bucket list with mocks using doReturn().when() to avoid UnfinishedStubbingException
        List<StringTermsBucket> buckets = new ArrayList<>();
        for (String productId : productIds) {
            StringTermsBucket bucket = mock(StringTermsBucket.class);
            // Create real FieldValue instead of mocking (since stringValue() is final)
            co.elastic.clients.elasticsearch._types.FieldValue fieldValue =
                    co.elastic.clients.elasticsearch._types.FieldValue.of(productId);
            doReturn(fieldValue).when(bucket).key();
            buckets.add(bucket);
        }

        // Now set up all the stubbing using doReturn().when() syntax
        doReturn(buckets).when(bucketContainer).array();
        doReturn(bucketContainer).when(termsAggregate).buckets();
        doReturn(termsAggregate).when(aggregate).sterms();

        // Create aggregations map
        Map<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate> aggregations = new HashMap<>();
        aggregations.put("unique_products", aggregate);

        doReturn(aggregations).when(searchResponse).aggregations();
        lenient().doReturn(hits).when(searchResponse).hits();

        return searchResponse;
    }
}
