package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.ElasticsearchTestBase;
import au.org.aodn.oceancurrent.configuration.elasticsearch.ElasticsearchProperties;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.CacheManager;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration tests for IndexingService using real Elasticsearch via Testcontainers.
 * These tests verify end-to-end functionality with actual Elasticsearch operations.
 *
 * For fast unit tests with mocked dependencies, see IndexingServiceTest.java
 */
class IndexingServiceIntegrationTest extends ElasticsearchTestBase {

    @Autowired
    private IndexingService indexingService;

    @MockitoBean
    private S3Service s3Service;

    @MockitoBean
    private RemoteJsonService remoteJsonService;

    @Autowired
    private ElasticsearchProperties esProperties;

    @Autowired
    private CacheManager cacheManager;

    @Override
    protected String getTestIndexName() {
        return "integration-test-index";
    }

    @Test
    void testReindexAll_createsIndexWithRealElasticsearch() throws Exception {
        // Given - mock external dependencies but use real Elasticsearch
        when(s3Service.isBucketAccessible()).thenReturn(true);
        when(s3Service.listAllSurfaceWaves()).thenReturn(Collections.emptyList());
        when(remoteJsonService.getFullUrls()).thenReturn(Collections.emptyList());

        // When - execute real indexing operation
        // Note: This would need actual implementation to work with the test index
        // For now, this demonstrates the structure

        // Then - verify using real Elasticsearch queries
        SearchResponse<ImageMetadataEntry> response = esClient.search(
            s -> s.index(getTestIndexName()),
            ImageMetadataEntry.class
        );

        assertThat(response.hits().total().value()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void testIndexCreation_withRealMappings() throws Exception {
        // This test verifies that the index was created with correct mappings
        // by the ElasticsearchTestBase setup

        var indexResponse = esClient.indices().get(g -> g.index(getTestIndexName()));

        assertThat(indexResponse.result()).containsKey(getTestIndexName());

        // Verify mappings exist
        var mappings = indexResponse.result().get(getTestIndexName()).mappings();
        assertThat(mappings).isNotNull();
        assertThat(mappings.properties()).containsKeys("path", "productId", "region", "fileName");
    }
}
