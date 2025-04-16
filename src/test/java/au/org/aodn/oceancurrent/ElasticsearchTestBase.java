package au.org.aodn.oceancurrent;

import au.org.aodn.oceancurrent.configuration.elasticsearch.ElasticsearchTestConfig;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

/**
 * Base class for tests that require Elasticsearch.
 * Uses Testcontainers to create an isolated Elasticsearch instance.
 * Automatically creates and deletes test indices for each test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ElasticsearchTestConfig.class)
@Testcontainers
public abstract class ElasticsearchTestBase {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchTestBase.class);

    @Autowired
    protected ElasticsearchClient esClient;

    @Autowired
    protected ElasticsearchContainer elasticsearchContainer;

    /**
     * The name of the test index to use.
     * Override this method to use a different test index name.
     */
    protected String getTestIndexName() {
        return "test_index";
    }

    /**
     * Create test index with mapping before each test
     */
    @BeforeEach
    public void createTestIndex() throws IOException {
        String indexName = getTestIndexName();
        log.info("Setting up test index: {}", indexName);

        // Check if index already exists
        boolean exists = esClient.indices().exists(
                ExistsRequest.of(e -> e.index(indexName))
        ).value();

        // Delete existing index if it exists
        if (exists) {
            log.info("Deleting existing index: {}", indexName);
            esClient.indices().delete(
                    DeleteIndexRequest.of(d -> d.index(indexName))
            );
        }

        // Create the test index with mapping - using fluent builder API
        log.info("Creating test index with mapping: {}", indexName);
        CreateIndexRequest request = CreateIndexRequest.of(r -> r
                .index(indexName)
                .settings(s -> s
                        .index(i -> i
                                .numberOfShards("1")
                                .numberOfReplicas("0")
                                .maxResultWindow(10000)
                        )
                )
                .mappings(m -> m
                        // Define your fields using the fluent API
                        .properties("id", p -> p.keyword(k -> k))
                        .properties("title", p -> p.text(t -> t))
                        .properties("created_at", p -> p.date(d -> d))
                        // Add additional standard fields
                        .properties("path", p -> p.keyword(k -> k))
                        .properties("productId", p -> p.keyword(k -> k))
                        .properties("region", p -> p.keyword(k -> k))
                        .properties("fileName", p -> p.keyword(k -> k))
                )
        );

        esClient.indices().create(request);
    }

    /**
     * Delete test index after each test
     */
    @AfterEach
    public void deleteTestIndex() throws IOException {
        String indexName = getTestIndexName();

        boolean exists = esClient.indices().exists(
                ExistsRequest.of(e -> e.index(indexName))
        ).value();

        if (exists) {
            log.info("Cleaning up test index: {}", indexName);
            esClient.indices().delete(
                    DeleteIndexRequest.of(d -> d.index(indexName))
            );
        }
    }

    /**
     * For test extensions that need additional mapping fields beyond the defaults.
     * Override this method to customize the properties for your specific test case.
     *
     * @param builder The mapping builder to augment with additional fields
     */
    protected void addCustomMappingProperties(CreateIndexRequest.Builder builder) {
        // Default implementation does nothing
        // Subclass can override to add their own custom mappings
    }
}
