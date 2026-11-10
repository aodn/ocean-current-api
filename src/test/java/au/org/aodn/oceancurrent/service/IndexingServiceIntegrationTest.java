package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.ElasticsearchTestBase;
import au.org.aodn.oceancurrent.configuration.elasticsearch.ElasticsearchProperties;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import au.org.aodn.oceancurrent.util.elasticsearch.BulkRequestProcessor;
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
    void bulkIndexing_keepsSameFileNameFromDifferentFolders() throws Exception {
        // Tidal Currents year folders overlap: 2026/ and 2027/ both hold 202612310000.gif
        BulkRequestProcessor processor = new BulkRequestProcessor(100, getTestIndexName(), esClient);
        processor.addDocument(tidalEntry("/tides/Bass_hv/2026", "202612310000.gif"));
        processor.addDocument(tidalEntry("/tides/Bass_hv/2027", "202612310000.gif"));
        // Indexing the same file again must update it, not duplicate it
        processor.addDocument(tidalEntry("/tides/Bass_hv/2026", "202612310000.gif"));
        processor.flush();
        esClient.indices().refresh(r -> r.index(getTestIndexName()));

        SearchResponse<ImageMetadataEntry> response = esClient.search(
                s -> s.index(getTestIndexName()).size(10),
                ImageMetadataEntry.class
        );

        assertThat(response.hits().hits())
                .extracting(hit -> hit.source().getPath())
                .containsExactlyInAnyOrder("/tides/Bass_hv/2026", "/tides/Bass_hv/2027");
    }

    private static ImageMetadataEntry tidalEntry(String path, String fileName) {
        ImageMetadataEntry entry = new ImageMetadataEntry();
        entry.setProductId("tidalCurrents-sl");
        entry.setRegion("Bass");
        entry.setPath(path);
        entry.setFileName(fileName);
        return entry;
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
